package rpcfifo;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;

public class GanttChartPanel extends JPanel {

    // --- Constantes de dibujo ---
    private static final int PIXELS_PER_INSTANCIA = 25;
    private static final int ROW_HEIGHT          = 22;
    private static final int HEADER_HEIGHT       = 40;
    private static final int LABEL_WIDTH         = 60;

    // --- Datos dinámicos ---
    private int ahora = 0;
    private List<Map<String, Object>> processes = new ArrayList<>();

    // Colores por CLIENTE (misma lógica en server y cliente)
    private final Map<String, Color> clientColorMap = new HashMap<>();

    // Fila fija por PROCESO (P-1, P-2, etc.)
    private final LinkedHashMap<String, Integer> processRowMap = new LinkedHashMap<>();

    private final Color[] PROCESS_COLORS = {
        new Color(76, 175, 80),   // Verde
        new Color(33, 150, 243),  // Azul
        new Color(255, 193, 7),   // Amarillo
        new Color(244, 67, 54),   // Rojo
        new Color(156, 39, 176),  // Morado
        new Color(0, 188, 212),   // Cyan
        new Color(255, 87, 34)    // Naranja
    };

    // --- Ventana de tiempo ---
    private boolean modoCiclico = false;   // server = true, cliente = false
    private static final int WINDOW_SIZE = 25;
    private int windowStartInstance = 0;
    private int maxTime = 30;              // sólo se usa cuando modoCiclico=false

    /** Permite activar/desactivar la ventana cíclica de 25 instancias */
    public void setModoCiclico(boolean activo) {
        this.modoCiclico = activo;
    }

    /**
     * Recibe el estado completo que viene del servidor (state + summary).
     * En el cliente, `state` y `summary` ya vienen filtrados a "mis procesos".
     */
    @SuppressWarnings("unchecked")
    public void actualizarDatos(Map<String, Object> state, Map<String, Object> summary) {
        if (state == null || summary == null) {
            this.processes = new ArrayList<>();
            this.ahora = 0;
            calcularTamanio();
            repaint();
            return;
        }

        this.ahora = (Integer) state.get("now");
        this.processes = new ArrayList<>();

        // 1) TERMINADOS (HISTORIAL)
        Object[] rows = (Object[]) summary.get("rows");
        if (rows != null) {
            for (Object o : rows) {
                Map<String, Object> m = (Map<String, Object>) o;
                m.put("type", "HISTORIAL");
                processes.add(m);
            }
        }

        // 2) RUNNING
        if (state.get("running") != null) {
            Map<String, Object> m = (Map<String, Object>) state.get("running");
            m.put("type", "RUNNING");
            processes.add(m);
        }

        // 3) FUTURE
        if (state.get("future") != null) {
            Object[] future = (Object[]) state.get("future");
            for (Object o : future) {
                Map<String, Object> m = (Map<String, Object>) o;
                m.put("type", "FUTURE");
                processes.add(m);
            }
        }

        // 4) READY
        if (state.get("ready") != null) {
            Object[] ready = (Object[]) state.get("ready");
            for (Object o : ready) {
                Map<String, Object> m = (Map<String, Object>) o;
                m.put("type", "READY");
                processes.add(m);
            }
        }

        // Actualizar filas fijas por proceso
        for (Map<String, Object> p : processes) {
            String name = (String) p.get("name");
            processRowMap.putIfAbsent(name, processRowMap.size());
        }

        // Lógica de ventana
        if (modoCiclico) {
            int bloqueActual = (this.ahora <= 0) ? 0 : (this.ahora - 1) / WINDOW_SIZE;
            this.windowStartInstance = bloqueActual * WINDOW_SIZE;
        } else {
            this.windowStartInstance = 0;
            this.maxTime = Math.max(30, this.ahora + 5); // cliente: crece hacia la derecha
        }

        calcularTamanio();
        repaint();
    }

    private void calcularTamanio() {
        int timeSpan = modoCiclico ? WINDOW_SIZE : maxTime;
        int totalWidth  = LABEL_WIDTH + (timeSpan * PIXELS_PER_INSTANCIA) + 50;
        int totalHeight = HEADER_HEIGHT + (processRowMap.size() * ROW_HEIGHT) + 20;
        setPreferredSize(new Dimension(totalWidth, totalHeight));
        revalidate();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Fondo
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, getWidth(), getHeight());

        drawTimeline(g2);

        if (processes == null || processes.isEmpty()) {
            return;
        }

        int yOffset = HEADER_HEIGHT + 10;
        List<String> pNames = new ArrayList<>(processRowMap.keySet());

        for (String pName : pNames) {

            // Nombre del proceso en la izquierda
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            g2.drawString(pName, 5, yOffset + 15);

            Map<String, Object> procData = null;
            for (Map<String, Object> p : processes) {
                if (p.get("name").equals(pName)) {
                    procData = p;
                    // Preferimos RUNNING / HISTORIAL si existen
                    String type = (String) p.get("type");
                    if ("RUNNING".equals(type) || "HISTORIAL".equals(type)) break;
                }
            }

            if (procData != null) {
                String type   = (String) procData.get("type");
                int t         = getInt(procData, "t");
                int C         = getInt(procData, "C");
                int start     = getInt(procData, "start");
                int finish    = getInt(procData, "finish");
                int E         = getInt(procData, "E");
                String clientId = (String) procData.get("clientId");
                Color color   = getClientColor(clientId);

                if ("HISTORIAL".equals(type)) {
                    int C_orig = start - E;
                    drawBar(g2, C_orig, start, yOffset, new Color(224, 224, 224), false); // espera
                    drawBar(g2, start, finish, yOffset, color, false);                    // ejecución
                }
                else if ("RUNNING".equals(type)) {
                    // Espera
                    drawBar(g2, C, start, yOffset, new Color(224, 224, 224), false);

                    int transcurrido = Math.max(0, ahora - start);
                    int ejecutadoFin = start + transcurrido;

                    // Ejecutado
                    drawBar(g2, start, ejecutadoFin, yOffset, color, false);

                    // Restante en tono más claro, delgado
                    int restante = t - transcurrido;
                    if (restante > 0) {
                        int startRest = ejecutadoFin;
                        int endRest   = start + t;

                        int vStart = Math.max(startRest, windowStartInstance);
                        int vEnd   = Math.min(endRest, windowStartInstance + (modoCiclico ? WINDOW_SIZE : maxTime));

                        if (vEnd > vStart) {
                            int x = LABEL_WIDTH + ((vStart - windowStartInstance) * PIXELS_PER_INSTANCIA);
                            int w = (vEnd - vStart) * PIXELS_PER_INSTANCIA;

                            g2.setColor(color.brighter());
                            g2.fillRect(x, yOffset + 4, w, 6); // más delgadito y centrado
                        }
                    }
                }
                else if ("READY".equals(type)) {
                    // Esperando en la cola desde C hasta AHORA
                    drawBar(g2, C, ahora, yOffset, new Color(224, 224, 224), false);
                }
                else if ("FUTURE".equals(type)) {
                    // Proceso que todavía no llega: barra fantasma
                    drawBar(g2, C, C + t, yOffset, Color.LIGHT_GRAY, true);
                }
            }

            yOffset += ROW_HEIGHT;
        }
    }

    private int getInt(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v instanceof Integer) ? (Integer) v : 0;
    }

    /** Color por CLIENTE (mismo cliente = mismo color en server y cliente) */
    private Color getClientColor(String clientId) {
    if (clientId == null) {
        return Color.GRAY;  // Por si algún proceso no trae clientId
    }

    // Si ya lo habíamos calculado, lo reutilizamos
    if (clientColorMap.containsKey(clientId)) {
        return clientColorMap.get(clientId);
    }

    // Índice determinista a partir del hash del clientId
    int idx = Math.abs(clientId.hashCode()) % PROCESS_COLORS.length;
    Color assigned = PROCESS_COLORS[idx];

    clientColorMap.put(clientId, assigned);
    return assigned;
}



    /** Dibuja una barra recortada a la ventana visible */
    private void drawBar(Graphics2D g2, int startVal, int endVal, int y, Color c, boolean ghost) {
        int ventanaFin = modoCiclico ? (windowStartInstance + WINDOW_SIZE) : maxTime;

        int visualStart = Math.max(startVal, windowStartInstance);
        int visualEnd   = Math.min(endVal, ventanaFin);

        if (visualEnd <= visualStart) return;

        int x = LABEL_WIDTH + ((visualStart - windowStartInstance) * PIXELS_PER_INSTANCIA);
        int w = (visualEnd - visualStart) * PIXELS_PER_INSTANCIA;

        if (ghost) {
            g2.setColor(new Color(245, 245, 245));
            g2.fillRect(x, y, w, ROW_HEIGHT - 8);
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawRect(x, y, w, ROW_HEIGHT - 8);
        } else {
            g2.setColor(c);
            g2.fillRect(x, y, w, ROW_HEIGHT - 8);
        }
    }

    /** Dibuja la línea de tiempo (0..25 ó 0..N según el modo) */
    private void drawTimeline(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setFont(new Font("Arial", Font.PLAIN, 10));

        int ventanaFin = modoCiclico ? (windowStartInstance + WINDOW_SIZE) : maxTime;

        for (int i = windowStartInstance; i <= ventanaFin; i++) {
            int x = LABEL_WIDTH + ((i - windowStartInstance) * PIXELS_PER_INSTANCIA);

            g2.setColor(new Color(230, 230, 230));
            g2.drawLine(x, HEADER_HEIGHT - 5, x, getHeight());

            g2.setColor(Color.BLACK);
            String label;

            if (modoCiclico) {
                // Server: mostrar sólo 0..25 aunque realmente vayamos en 26,27,...
                int val = ((i - 1) % WINDOW_SIZE) + 1;
                if (i == 0) val = 0;
                label = String.valueOf(val);
            } else {
                // Cliente: mostrar el valor real 0,1,2,3,...
                label = String.valueOf(i);
            }
            g2.drawString(label, x - 3, HEADER_HEIGHT - 10);
        }
    }
}
