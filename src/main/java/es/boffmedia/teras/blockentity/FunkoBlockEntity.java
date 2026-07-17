package es.boffmedia.teras.blockentity;

import es.boffmedia.teras.Teras;
import es.boffmedia.teras.init.BlockEntityInit;
import es.boffmedia.teras.init.ComponentInit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Holds the skin a placed funko should display. Two mutually exclusive modes:
 * <ul>
 *     <li>{@code profile} &mdash; a player, resolved and rendered exactly like a player head.</li>
 *     <li>{@code skin_file} &mdash; a PNG filename inside {@code Teras/skins/}.</li>
 * </ul>
 *
 * <p><b>Storage moved from NBT to data components.</b> 1.16.5 kept both in the item's
 * {@code BlockEntityTag} under {@code SkinOwner}/{@code SkinFile}, mirroring vanilla skull semantics
 * so a funko could be minted with {@code player_head{SkullOwner:...}}-shaped NBT. 1.20.5 deleted
 * {@code BlockEntityTag}, and vanilla skulls moved to {@link DataComponents#PROFILE}, so the owner is
 * now that same vanilla component and the file is {@link ComponentInit#FUNKO_SKIN_FILE}. The intent
 * survives intact — and improves: {@code SkinOwner} accepted "either a resolved profile compound or a
 * bare player-name string", which is precisely what {@link ResolvableProfile#CODEC} already is, so
 * {@code /give @p teras:funko[minecraft:profile="Notch"]} works like {@code player_head}.</p>
 *
 * <p>Implicit components ({@link #applyImplicitComponents}/{@link #collectImplicitComponents}) make
 * placement, drops and pick-block carry the skin with no per-path copying — replacing 1.16.5's
 * hand-rolled {@code writeToItem}.</p>
 */
public class FunkoBlockEntity extends BlockEntity {

    private static final String TAG_PROFILE = "profile";
    private static final String TAG_SKIN_FILE = "skin_file";

    @Nullable
    private ResolvableProfile owner;
    @Nullable
    private String skinFile;

    public FunkoBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityInit.FUNKO.get(), pos, state);
    }

    public void setOwner(@Nullable ResolvableProfile profile) {
        this.skinFile = null;
        this.owner = profile;
        updateOwnerProfile();
    }

    public void setSkinFile(@Nullable String file) {
        this.owner = null;
        this.skinFile = (file == null || file.isEmpty()) ? null : file;
        setChanged();
        syncToClients();
    }

    @Nullable
    public ResolvableProfile getOwnerProfile() {
        return this.owner;
    }

    @Nullable
    public String getSkinFile() {
        return this.skinFile;
    }

    /**
     * Resolves a name-only profile into one carrying texture properties, then re-syncs.
     *
     * <p>Only the server can do this: the profile caches behind {@link ResolvableProfile#resolve()}
     * are installed by {@code SkullBlockEntity.setup} server-side, and the client's
     * {@code SkinManager} keys its lookup on the profile's texture properties — so a client handed a
     * bare name renders Steve. Hence the {@link #syncToClients()} once resolution lands, which is
     * what 1.16.5 did too (vanilla skulls settle for {@code setChanged()} and can show a stale head
     * until the chunk reloads).</p>
     */
    private void updateOwnerProfile() {
        if (this.owner != null && !this.owner.isResolved()) {
            this.owner.resolve().thenAcceptAsync(resolved -> {
                this.owner = resolved;
                setChanged();
                syncToClients();
            }, SkullBlockEntity.CHECKED_MAIN_THREAD_EXECUTOR);
        } else {
            setChanged();
            syncToClients();
        }
    }

    private void syncToClients() {
        if (this.level != null && !this.level.isClientSide) {
            BlockState state = getBlockState();
            this.level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.owner = null;
        this.skinFile = null;
        if (tag.contains(TAG_SKIN_FILE, CompoundTag.TAG_STRING)) {
            this.skinFile = tag.getString(TAG_SKIN_FILE);
        } else if (tag.contains(TAG_PROFILE)) {
            ResolvableProfile.CODEC
                    .parse(NbtOps.INSTANCE, tag.get(TAG_PROFILE))
                    .resultOrPartial(error -> Teras.LOGGER.error("Failed to load funko profile: {}", error))
                    .ifPresent(this::setOwner);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.owner != null) {
            tag.put(TAG_PROFILE, ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, this.owner).getOrThrow());
        }
        if (this.skinFile != null) {
            tag.putString(TAG_SKIN_FILE, this.skinFile);
        }
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        // Order matters: each setter clears the other mode, so the file wins only when actually set.
        this.owner = componentInput.get(DataComponents.PROFILE);
        this.skinFile = componentInput.get(ComponentInit.FUNKO_SKIN_FILE.get());
        if (this.skinFile != null) {
            this.owner = null;
        } else if (this.owner != null) {
            updateOwnerProfile();
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.PROFILE, this.owner);
        components.set(ComponentInit.FUNKO_SKIN_FILE.get(), this.skinFile);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove(TAG_PROFILE);
        tag.remove(TAG_SKIN_FILE);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
