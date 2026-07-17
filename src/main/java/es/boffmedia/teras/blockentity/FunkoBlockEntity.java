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
 * Holds the skin a placed funko displays: either a player {@code profile} (rendered exactly like a
 * player head) or a {@code skin_file} PNG inside {@code Teras/skins/}. The two are mutually exclusive.
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
     * Only the server can resolve: the caches behind {@link ResolvableProfile#resolve()} are
     * installed server-side by {@code SkullBlockEntity.setup}, and the client's {@code SkinManager}
     * keys its lookup on the profile's texture properties, so a client handed a bare name renders
     * Steve. Hence the re-sync once resolution lands.
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
