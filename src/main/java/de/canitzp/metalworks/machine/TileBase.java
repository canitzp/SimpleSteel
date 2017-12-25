package de.canitzp.metalworks.machine;

import de.canitzp.metalworks.network.NetworkHandler;
import de.canitzp.metalworks.network.packet.PacketSyncTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

/**
 * @author canitzp
 */
public class TileBase extends TileEntity {

    @Override
    public final void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.readNBT(compound, NBTType.SAVE);
    }

    @Override
    public final NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        this.writeNBT(compound, NBTType.SAVE);
        return compound;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        return this.getCapability(capability, facing) != null;
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        if(capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY){
            IItemHandler inventory = getInventory(facing);
            if(inventory != null){
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
            }
        }
        if(capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY){
            IFluidHandler tank = getTank(facing);
            if(tank != null){
                return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(tank);
            }
        }
        if(capability == CapabilityEnergy.ENERGY){
            IEnergyStorage energy = getEnergy(facing);
            if(energy != null){
                return CapabilityEnergy.ENERGY.cast(energy);
            }
        }
        return null;
    }

    @Nullable
    protected IItemHandler getInventory(@Nullable EnumFacing side){
        return null;
    }

    @Nullable
    protected IEnergyStorage getEnergy(@Nullable EnumFacing side){
        return null;
    }

    @Nullable
    protected IFluidHandler getTank(@Nullable EnumFacing side){
        return null;
    }

    public void writeNBT(NBTTagCompound nbt, NBTType type){
        NBTTagCompound caps = new NBTTagCompound();
        for(EnumFacing side : EnumFacing.values()){
            NBTTagCompound capsSided = new NBTTagCompound();
            this.writeCapabilities(capsSided, side);
            caps.setTag(side.toString().toLowerCase(Locale.ROOT), capsSided);
        }
        nbt.setTag("TileBaseCapabilities", caps);
    }

    public void readNBT(NBTTagCompound nbt, NBTType type){
        NBTTagCompound caps = nbt.getCompoundTag("TileBaseCapabilities");
        for(EnumFacing side : EnumFacing.values()){
            String name = side.toString().toLowerCase(Locale.ROOT);
            if(caps.hasKey(name, Constants.NBT.TAG_COMPOUND)){
                this.readCapabilities(caps.getCompoundTag(name), side);
            }
        }
    }

    protected void readCapabilities(NBTTagCompound nbt, @Nullable EnumFacing side){
        IItemHandler inventory = this.getInventory(side);
        if(inventory != null && inventory instanceof IItemHandlerModifiable && nbt.hasKey("Inventory")){
            CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.readNBT(inventory, side, nbt.getTag("Inventory"));
        }
        IFluidHandler tank = getTank(side);
        if(tank != null && tank instanceof IFluidTank && nbt.hasKey("FluidTank")){
            CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.readNBT(tank, side, nbt.getCompoundTag("FluidTank"));
        }
        IEnergyStorage energy = getEnergy(side);
        if(energy != null && energy instanceof EnergyStorage && nbt.hasKey("Energy")){
            CapabilityEnergy.ENERGY.readNBT(energy, side, nbt.getTag("Energy"));
        }
    }

    protected void writeCapabilities(NBTTagCompound nbt, @Nullable EnumFacing side){
        IItemHandler inventory = this.getInventory(side);
        if(inventory != null && inventory instanceof IItemHandlerModifiable){
            nbt.setTag("Inventory", CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.writeNBT(inventory, side));
        }
        IFluidHandler tank = getTank(side);
        if(tank != null && tank instanceof IFluidTank){
            nbt.setTag("FluidTank", CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.writeNBT(tank, side));
        }
        IEnergyStorage energy = getEnergy(side);
        if(energy != null && energy instanceof EnergyStorage){
            nbt.setTag("Energy", CapabilityEnergy.ENERGY.writeNBT(energy, side));
        }
    }

    private boolean isSyncDirty = false;
    public void syncToClients(){
        if(this.world != null && !this.world.isRemote){
            if(world.getTotalWorldTime() % 10 == 0){
                NBTTagCompound syncTag = new NBTTagCompound();
                this.writeNBT(syncTag, NBTType.SYNC);
                for(EntityPlayer player : this.world.playerEntities){
                    if(player instanceof EntityPlayerMP && player.getDistance(pos.getX(), pos.getY(), pos.getZ()) <= 64){
                        NetworkHandler.NET.sendTo(new PacketSyncTile(syncTag, this.pos), (EntityPlayerMP) player);
                    }
                }
                this.isSyncDirty = false;
            } else {
                this.isSyncDirty = true;
            }
        }
    }

    @SideOnly(Side.CLIENT)
    public void markForRenderUpdate(){
        if(this.world != null && this.world.isRemote){
            this.world.markBlockRangeForRenderUpdate(this.pos, this.pos);
        }
    }

    protected void updateForSyncing(){
        if(this.isSyncDirty){
            this.syncToClients();
        }
    }

    @SideOnly(Side.CLIENT)
    public void onSyncPacket(){}

    public enum NBTType{
        SAVE,
        DROP,
        SYNC
    }
}