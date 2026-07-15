package es.boffmedia.teras.util.math;

import net.minecraft.util.Direction;

public class CreateFrameBox {

    public static AlignedBox getBox(AlignedBox box, Direction d, int ancho, int alto, int posX, int posY) {
        if(posX == 0 && posY == 0) return fromBottomLeft(box, d, ancho, alto);
        if(posX == 0 && posY == 1) return fromCenterLeft(box, d, ancho, alto);
        if(posX == 0 && posY == 2) return fromTopLeft(box, d, ancho, alto);

        if(posX == 1 && posY == 0) return fromBottomCenter(box, d, ancho, alto);
        if(posX == 1 && posY == 1) return fromCenter(box, d, ancho, alto);
        if(posX == 1 && posY == 2) return fromTopCenter(box, d, ancho, alto);

        if(posX == 2 && posY == 0) return fromBottomRight(box, d, ancho, alto);
        if(posX == 2 && posY == 1) return fromCenterRight(box, d, ancho, alto);
        if(posX == 2 && posY == 2) return fromTopRight(box, d, ancho, alto);


        return box;
    }



    public static AlignedBox fromBottomLeft(AlignedBox box, Direction d, int ancho, int alto ){
        if (d == Direction.WEST) {
            box = new AlignedBox(0, 0, 0, 1, alto, ancho );
        }

        if (d == Direction.SOUTH) { // NORTE, ta bug
            box = new AlignedBox(0, 0, 0, ancho, alto, 1);
        }

        if (d == Direction.EAST) {
            box = new AlignedBox(0, 0, -ancho + 1, 1, alto, 1 );
        }

        if (d == Direction.NORTH) {
            box = new AlignedBox(-ancho +1, 0, 0, 1, alto, 0);
        }
        return box;
    }

    private static AlignedBox fromTopLeft(AlignedBox box, Direction d, int ancho, int alto) {
        if (d == Direction.WEST) {
            box = new AlignedBox(0, -alto + 1, 0, 1, 1, ancho );
        }

        if (d == Direction.SOUTH) { // NORTE, ta bug
            box = new AlignedBox(0, -alto + 1, 0, ancho, 1, 1);
        }

        if (d == Direction.EAST) {
            box = new AlignedBox(0, -alto + 1, -ancho + 1, 1, 1, 1 );
        }

        if (d == Direction.NORTH) {
            box = new AlignedBox(-ancho +1, -alto + 1, 0, 1, 1, 0);
        }

        return box;
    }



    private static AlignedBox fromCenterLeft(AlignedBox box, Direction d, int ancho, int alto) {
        if (d == Direction.WEST) {
            box = new AlignedBox(0, (float) (0 + (-alto + 1)) / 2, 0, 1, (alto + 1) /2f, ancho );
        }

        if (d == Direction.SOUTH) { // NORTE, ta bug
            box = new AlignedBox(0, (float) (0 + (-alto + 1)) / 2, 0, ancho, (alto + 1) /2f, 1);
        }

        if (d == Direction.EAST) {
            box = new AlignedBox(0, (float) (0 + (-alto + 1)) / 2, -ancho + 1, 1, (alto + 1) /2f, 1 );
        }

        if (d == Direction.NORTH) {
            box = new AlignedBox(-ancho +1, (float) (0 + (-alto + 1)) / 2, 0, 1, (alto + 1) /2f, 0);
        }


        return box;
    }



    private static AlignedBox fromBottomCenter(AlignedBox box, Direction d, int ancho, int alto) {
        if (d == Direction.WEST) {
            //box = new AlignedBox(0, 0, 0, 1, alto, ancho );
            //box = new AlignedBox(0, 0, -ancho + 1, 1, alto, 1 );
            // to center, we have to average the two
            box = new AlignedBox(0, 0, (float) (0 + (-ancho + 1)) / 2, 1, alto, (ancho + 1) /2f );
        }

        if (d == Direction.SOUTH) { // NORTE, ta bug
            // to center, we have to average the two
            box = new AlignedBox((float) (0 + (-ancho + 1)) / 2, 0, 0, (ancho + 1) /2f, alto, 1);

        }

        if (d == Direction.EAST) {
            // to center, we have to average the two
            box = new AlignedBox(0, 0, (float) (0 + (-ancho + 1)) / 2, 1, alto, (ancho + 1) /2f );
        }

        if (d == Direction.NORTH) {
            // to center, we have to average the two
            box = new AlignedBox((float) (0 + (-ancho + 1)) / 2, 0, 0, (ancho + 1) /2f, alto, 1);
        }

        return box;
    }

    private static AlignedBox fromTopCenter(AlignedBox box, Direction d, int ancho, int alto) {
        if (d == Direction.WEST) {
            //box = new AlignedBox(0, -alto + 1, 0, 1, 1, ancho );
            //box = new AlignedBox(0, -alto + 1, -ancho + 1, 1, 1, 1 );
            // to center, we have to average the two
            box = new AlignedBox(0, -alto + 1, (float) (0 + (-ancho + 1)) / 2, 1, 1, (ancho + 1) /2f );
        }

        if (d == Direction.SOUTH) { // NORTE, ta bug
            // to center, we have to average the two
            box = new AlignedBox((float) (0 + (-ancho + 1)) / 2, -alto + 1, 0, (ancho + 1) /2f, 1, 1);
        }

        if (d == Direction.EAST) {
            // to center, we have to average the two
            box = new AlignedBox(0, -alto + 1, (float) (0 + (-ancho + 1)) / 2, 1, 1, (ancho + 1) /2f );
        }

        if (d == Direction.NORTH) {
            // to center, we have to average the two
            box = new AlignedBox((float) (0 + (-ancho + 1)) / 2, -alto + 1, 0, (ancho + 1) /2f, 1, 1);
        }

        return box;
    }

    private static AlignedBox fromCenter(AlignedBox box, Direction d, int ancho, int alto) {
        if (d == Direction.WEST) {
            box = new AlignedBox(0, (float) (0 + (-alto + 1)) / 2, (float) (0 + (-ancho + 1)) / 2, 1, (alto + 1) /2f, (ancho + 1) /2f );
        }

        if (d == Direction.SOUTH) { // NORTE, ta bug
            box = new AlignedBox((float) (0 + (-ancho + 1)) / 2, (float) (0 + (-alto + 1)) / 2, 0, (ancho + 1) /2f, (alto + 1) /2f, 1);
        }

        if (d == Direction.EAST) {
            box = new AlignedBox(0, (float) (0 + (-alto + 1)) / 2, (float) (0 + (-ancho + 1)) / 2, 1, (alto + 1) /2f, (ancho + 1) /2f );
        }

        if (d == Direction.NORTH) {
            box = new AlignedBox((float) (0 + (-ancho + 1)) / 2, (float) (0 + (-alto + 1)) / 2, 0, (ancho + 1) /2f, (alto + 1) /2f, 1);
        }

        return box;

    }

    public static AlignedBox fromBottomRight(AlignedBox box, Direction d, int ancho, int alto ){
        if (d == Direction.WEST) {
            box = new AlignedBox(0, 0, -ancho + 1, 1, alto, 1 );
        }

        if (d == Direction.SOUTH) { // NORTE, ta bug
            box = new AlignedBox(-ancho +1, 0, 0, 1, alto, 1);
        }

        if (d == Direction.EAST) {
            box = new AlignedBox(0, 0, 0, 1, alto, ancho );
        }

        if (d == Direction.NORTH) {
            box = new AlignedBox(0, 0, 0, ancho, alto, 1);
        }

        return box;
    }



    private static AlignedBox fromTopRight(AlignedBox box, Direction d, int ancho, int alto) {
        if (d == Direction.WEST) {
            box = new AlignedBox(0, -alto + 1, -ancho + 1, 1, 1, 1 );
        }

        if (d == Direction.SOUTH) { // NORTE, ta bug
            box = new AlignedBox(-ancho +1, -alto + 1, 0, 1, 1, 1);
        }

        if (d == Direction.EAST) {
            box = new AlignedBox(0, -alto + 1, 0, 1, 1, ancho );
        }

        if (d == Direction.NORTH) {
            box = new AlignedBox(0, -alto + 1, 0, ancho, 1, 1);
        }

        return box;
    }


    private static AlignedBox fromCenterRight(AlignedBox box, Direction d, int ancho, int alto) {
        if (d == Direction.WEST) {
            box = new AlignedBox(0, (float) (0 + (-alto + 1)) / 2, -ancho + 1, 1, (alto + 1) /2f, 1 );
        }

        if (d == Direction.SOUTH) { // NORTE, ta bug
            box = new AlignedBox(-ancho +1, (float) (0 + (-alto + 1)) / 2, 0, 1, (alto + 1) /2f, 1);
        }

        if (d == Direction.EAST) {
            box = new AlignedBox(0, (float) (0 + (-alto + 1)) / 2, 0, 1, (alto + 1) /2f, ancho );
        }

        if (d == Direction.NORTH) {
            box = new AlignedBox(0, (float) (0 + (-alto + 1)) / 2, 0, ancho, (alto + 1) /2f, 1);
        }

        return box;
    }


}
