package es.boffmedia.teras.util.objects._old.karts;

public enum EstadoCarrera {
    BUSCANDO("BUSCANDO", 1),
    INICIANDO("INICIANDO",2),
    EN_CURSO("EN_CURSO",3),
    FINALIZADA("FINALIZADA",4);


    private int valor;
    private String nombre;

    EstadoCarrera(String nombre, int valor) {
        this.nombre = nombre;
        this.valor = valor;
    }

    public int getValor() {
        return valor;
    }

    public String getNombre() {
        return nombre;
    }
}
