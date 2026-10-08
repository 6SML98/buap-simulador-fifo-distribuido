package rpcfifo;

public class Result {
    public final String name;
    public final int start;
    public final int finish;
    public final int E;    
    public final int t;    
    public final int F;    
    public final double P; 
    
    // ¡NUEVO CAMPO!
    public final String clientId;

    // ¡ACTUALIZA EL CONSTRUCTOR!
    public Result(String name, int start, int finish, int E, int t, String clientId) {
        this.name = name;
        this.start = start;
        this.finish = finish;
        this.E = E;
        this.t = t;
        this.F = t + E;
        this.P = (t > 0) ? (double) F / (double) t : 0.0;
        this.clientId = clientId; // Guárdalo aquí
    }
}