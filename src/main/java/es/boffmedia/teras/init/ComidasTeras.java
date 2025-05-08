package es.boffmedia.teras.init;

import es.boffmedia.teras.items.TerasItemGroup;
import es.boffmedia.teras.util.objects._old.ObjColocable;
import net.minecraft.block.Block;
import net.minecraft.item.Food;
import net.minecraft.item.Item;
import net.minecraft.item.UseAction;
import net.minecraft.util.math.shapes.VoxelShape;

import java.util.ArrayList;

public class ComidasTeras {
    static ArrayList<ObjColocable> objetos = new ArrayList<>();

    private static void initObjetos() {
        objetos.add(nuevaComida("caca_de_waifu", 2,2,true, Block.box(3, 0, 4, 12, 4.25, 13)));
        objetos.add(nuevaBebida("sake", 1,0,true, Block.box(7, 0, 7, 9, 1, 9)));
        objetos.add(nuevaBebida("itamilk", 1,0,true, Block.box(5, 0, 5, 11, 12, 10)));
    }

    public static ArrayList<ObjColocable> getComidas(){
        initObjetos();
        return objetos;
    }

    static ObjColocable nuevaComida(String nombre, int nutricion, int saturacion, boolean rapido, VoxelShape hitbox){
        return new ObjColocable(nombre, getPropertiesComida(nutricion, saturacion, rapido), hitbox, UseAction.EAT);
    }

    static ObjColocable nuevaBebida(String nombre, int nutricion, int saturacion, boolean rapido, VoxelShape hitbox){
        return new ObjColocable(nombre, getPropertiesComida(nutricion, saturacion, rapido), hitbox, UseAction.DRINK);
    }

    static Item.Properties getPropertiesComida(int nutricion, int saturacion, boolean rapido){
        Food.Builder builder = new Food.Builder()
                .nutrition(nutricion)
                .saturationMod(saturacion);
        if(rapido) builder.fast();

        return new Item.Properties().tab(TerasItemGroup.LIZARDON_GROUP).food(builder.build());
    }
}
