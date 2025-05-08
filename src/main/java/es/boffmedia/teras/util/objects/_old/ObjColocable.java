package es.boffmedia.teras.util.objects._old;

import net.minecraft.item.Item;
import net.minecraft.item.UseAction;
import net.minecraft.util.math.shapes.VoxelShape;

public class ObjColocable extends Item{
    private String nombre;
    private Item.Properties properties;
    private VoxelShape hitbox;
    private UseAction action;

    public ObjColocable(String nombre, Item.Properties properties, VoxelShape hitbox) {
        this(nombre, properties, hitbox, UseAction.NONE);
    }

    public ObjColocable(String nombre, Item.Properties properties, VoxelShape hitbox, UseAction action) {
        super(properties);

        this.nombre = nombre;
        this.properties = properties;
        this.hitbox = hitbox;
        this.action = action;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Item.Properties getProperties() {
        return properties;
    }

    public void setProperties(Item.Properties properties) {
        this.properties = properties;
    }

    public VoxelShape getHitbox() {
        return hitbox;
    }

    public void setHitbox(VoxelShape hitbox) {
        this.hitbox = hitbox;
    }

    public UseAction getAction() {
        return action;
    }

    public void setAction(UseAction action) {
        this.action = action;
    }
}
