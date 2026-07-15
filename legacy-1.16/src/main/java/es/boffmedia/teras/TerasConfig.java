package es.boffmedia.teras;

import net.minecraftforge.common.ForgeConfigSpec;

public class TerasConfig {
    protected static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.ConfigValue<Integer> ESCALA_CARTELES;

    public static final ForgeConfigSpec SPEC;


    static {
        BUILDER.comment("Teras Config");
        BUILDER.push("teras");


        ESCALA_CARTELES = BUILDER
                .comment("Escala de los carteles")
                .define("escalaCarteles", 1);




        SPEC = BUILDER.build();

    }
}
