package es.boffmedia.teras.tileentity;

import com.mojang.authlib.GameProfile;
import es.boffmedia.teras.init.TileEntityInit;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.SkullTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StringUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Holds the skin a placed funko should display. Two mutually exclusive modes:
 * <ul>
 *     <li>{@code owner} &mdash; a {@link GameProfile} resolved like a player head
 *     (server-side, then synced to clients with its texture properties).</li>
 *     <li>{@code skinFile} &mdash; a PNG filename inside {@code Teras/skins/}.</li>
 * </ul>
 * NBT keys mirror vanilla skull semantics ({@code SkinOwner} accepts either a resolved
 * profile compound or a bare player-name string) so funko items can be created with
 * simple {@code BlockEntityTag} NBT, exactly like {@code player_head{SkullOwner:...}}.
 */
public class FunkoTE extends TileEntity {

    public static final String TAG_OWNER = "SkinOwner";
    public static final String TAG_FILE = "SkinFile";

    @Nullable
    private GameProfile owner;
    @Nullable
    private String skinFile;

    public FunkoTE() {
        super(TileEntityInit.FUNKO_TE.get());
    }

    public void setOwner(@Nullable GameProfile profile) {
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

    private void updateOwnerProfile() {
        // Fills the UUID/textures from the profile cache + session service (no-op if already complete).
        this.owner = SkullTileEntity.updateGameprofile(this.owner);
        setChanged();
        syncToClients();
    }

    private void syncToClients() {
        if (this.level != null && !this.level.isClientSide) {
            BlockState state = getBlockState();
            this.level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Nullable
    @OnlyIn(Dist.CLIENT)
    public GameProfile getOwnerProfile() {
        return this.owner;
    }

    @Nullable
    @OnlyIn(Dist.CLIENT)
    public String getSkinFile() {
        return this.skinFile;
    }

    /** Writes this funko's skin into an item stack's {@code BlockEntityTag} so it survives breaking/picking. */
    public void writeToItem(ItemStack stack) {
        CompoundNBT tag = new CompoundNBT();
        if (this.owner != null) {
            CompoundNBT profile = new CompoundNBT();
            NBTUtil.writeGameProfile(profile, this.owner);
            tag.put(TAG_OWNER, profile);
        }
        if (this.skinFile != null) {
            tag.putString(TAG_FILE, this.skinFile);
        }
        if (!tag.isEmpty()) {
            stack.addTagElement("BlockEntityTag", tag);
        }
    }

    @Override
    public void load(BlockState state, CompoundNBT nbt) {
        super.load(state, nbt);
        this.owner = null;
        this.skinFile = null;
        if (nbt.contains(TAG_FILE, 8)) {
            this.skinFile = nbt.getString(TAG_FILE);
        } else if (nbt.contains(TAG_OWNER, 10)) {
            setOwner(NBTUtil.readGameProfile(nbt.getCompound(TAG_OWNER)));
        } else if (nbt.contains(TAG_OWNER, 8)) {
            String name = nbt.getString(TAG_OWNER);
            if (!StringUtils.isNullOrEmpty(name)) {
                setOwner(new GameProfile((UUID) null, name));
            }
        }
    }

    @Override
    public CompoundNBT save(CompoundNBT compound) {
        super.save(compound);
        if (this.owner != null) {
            CompoundNBT profile = new CompoundNBT();
            NBTUtil.writeGameProfile(profile, this.owner);
            compound.put(TAG_OWNER, profile);
        }
        if (this.skinFile != null) {
            compound.putString(TAG_FILE, this.skinFile);
        }
        return compound;
    }

    @Override
    public CompoundNBT getUpdateTag() {
        return save(new CompoundNBT());
    }

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(worldPosition, 4, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        load(getBlockState(), pkt.getTag());
    }

    @Override
    public void handleUpdateTag(BlockState state, CompoundNBT tag) {
        load(state, tag);
    }
}
