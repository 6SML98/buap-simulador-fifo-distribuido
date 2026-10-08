package rpcfifo;

public class Proc implements Comparable<Proc> {
    public final String name;
    public final int C; 
    public final int t; 
    public int C_original; 
    public int startTime = -1; 
    
    // ¡NUEVO CAMPO!
    public final String clientId; 

    // ¡ACTUALIZA EL CONSTRUCTOR!
    public Proc(String name, int C, int t, String clientId) {
        this.name = name; 
        this.C = C; 
        this.t = t;
        this.C_original = C; 
        this.clientId = clientId; // Guárdalo aquí
    }

    @Override
    public int compareTo(Proc o) {
        if (this.C != o.C) return Integer.compare(this.C, o.C);
        return this.name.compareTo(o.name);
    }
}