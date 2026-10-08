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

    // --- Constantes ---
    private static final int PIXELS_PER_INSTANCIA = 25;
    private static final int ROW_HEIGHT = 22;
    private static final int HEADER_HEIGHT = 40;
    private static final int LABEL_WIDTH = 60;

    // --- Datos ---
    private int ahora = 0;
    private List<Map<String, Object>> processes;
    
    private final Map<String, Color> clientColorMap = new HashMap<>();
    private final LinkedHashMap<String, Integer> processRowMap = new LinkedHashMap<>();

    // Mapa de colores y filas
    private final Color[] PROCESS_COLORS = {
        new Color(76, 175, 80),  // Verde
        new Color(33, 150, 243), // Azul
        new Color(255, 193, 7),  // Amarillo
        new Color(244, 67, 54),  // Rojo
        new Color(156, 39, 176), // Púrpura
        new Color(0, 188, 212),  // Cyan
        new Color(255, 87, 34)   // Naranja
    };

    // Ventana
    private int maxTime = 30;
    private boolean modoCiclico = false;
    private static final int WINDOW_SIZE = 25;
    private int windowStartInstance = 0;

    public void setModoCiclico(boolean activo) {
        this.modoCiclico = activo;
    }

    public void actualizarDatos(Map<String, Object> state, Map<String, Object> summary) {
        this.ahora = (Integer) state.get("now");
        this.processes = new ArrayList<>();

        // 1. Recopilar todos los procesos
        if (summary.get("rows") != null) {
             Object[] rows = (Object[]) summary.get("rows");
             for(Object o : rows) { Map<String, Object> m = (Map<String, Object>)o; m.put("type", "HISTORIAL"); processes.add(m); }
        }
        if (state.get("running") != null) {
            Map<String, Object> m = (Map<String, Object>) state.get("running"); m.put("type", "RUNNING"); processes.add(m);
        }
        if (state.get("future") != null) {
            Object[] f = (Object[]) state.get("future");
            for(Object o : f) { Map<String, Object> m = (Map<String, Object>)o; m.put("type", "FUTURE"); processes.add(m); }
        }
        if (state.get("ready") != null) {
            Object[] r = (Object[]) state.get("ready");
            for(Object o : r) { Map<String, Object> m = (Map<String, Object>)o; m.put("type", "READY"); processes.add(m); }
        }

        // Actualizar filas
        for (Map<String, Object> p : processes) {
             String name = (String) p.get("name");
             processRowMap.putIfAbsent(name, processRowMap.size());
        }

        // Lógica de Ventana
        if (modoCiclico) {
        int bloqueActual = (this.ahora <= 0) ? 0 : (this.ahora - 1) / WINDOW_SIZE;
        this.windowStartInstance = bloqueActual * WINDOW_SIZE;
        this.maxTime = this.windowStartInstance + WINDOW_SIZE;
    }else {
            this.windowStartInstance = 0;
            this.maxTime = Math.max(30, this.ahora + 5);
        }

        calcularTamanio();
    }

    private void calcularTamanio() {
        int timeSpan = modoCiclico ? WINDOW_SIZE : maxTime;
        int totalWidth = LABEL_WIDTH + (timeSpan * PIXELS_PER_INSTANCIA) + 50;
        int totalHeight = HEADER_HEIGHT + (processRowMap.size() * ROW_HEIGHT) + 20;
        setPreferredSize(new Dimension(totalWidth, totalHeight));
        revalidate();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, getWidth(), getHeight());

        drawTimeline(g2);

        if (processes == null || processes.isEmpty()) return;

        int yOffset = HEADER_HEIGHT + 10;
        List<String> pNames = new ArrayList<>(processRowMap.keySet());

        for (String pName : pNames) {
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("Arial", Font.BOLD, 12));
            g2.drawString(pName, 5, yOffset + 15);

            Map<String, Object> procData = null;
            for (Map<String, Object> p : processes) {
                if (p.get("name").equals(pName)) {
                    procData = p;
                    if ("RUNNING".equals(p.get("type")) || "HISTORIAL".equals(p.get("type"))) break;
                }
            }

            if (procData != null) {
                String type = (String) procData.get("type");
                int t = getInt(procData, "t");
                int C = getInt(procData, "C"); 
                int start = getInt(procData, "start");
                int finish = getInt(procData, "finish");
                int E = getInt(procData, "E");
                
                String clientId = (String) procData.get("clientId");
                Color color = getClientColor(clientId);
                
                if ("HISTORIAL".equals(type)) {
                    int C_orig = start - E;
                    drawBar(g2, C_orig, start, yOffset, new Color(224, 224, 224), false);
                    drawBar(g2, start, finish, yOffset, color, false);
                } 
                else if ("RUNNING".equals(type)) {
                    // 1. Espera (Gris)
                    drawBar(g2, C, start, yOffset, new Color(224, 224, 224), false);
                    
                    // 2. Ejecutado (Color Sólido)
                    int transcurrido = Math.max(0, ahora - start);
                    drawBar(g2, start, start + transcurrido, yOffset, color, false);
                    
                    // 3. Restante (Color Claro + Centrado)
                    int restante = t - transcurrido;
                    if (restante > 0) {
                         int startRest = start + transcurrido;
                         int endRest = start + t;
                         
                         // Calcular recorte de ventana
                         int vStart = Math.max(startRest, windowStartInstance);
                         int vEnd = Math.min(endRest, maxTime);
                         
                         if (vEnd > vStart) {
                             int x = LABEL_WIDTH + ((vStart - windowStartInstance) * PIXELS_PER_INSTANCIA);
                             int w = (vEnd - vStart) * PIXELS_PER_INSTANCIA;
                             
                             g2.setColor(color.brighter()); // Color claro
                             // ¡AQUÍ ESTÁ EL AJUSTE! 
                             // Usamos +4 de offset y altura 6 para centrarlo perfectamente
                             g2.fillRect(x, yOffset + 4, w, 6); 
                         }
                    }
                }
                else if ("READY".equals(type)) {
                    drawBar(g2, C, ahora, yOffset, new Color(224, 224, 224), false);
                }
                else if ("FUTURE".equals(type)) {
                    drawBar(g2, C, C + t, yOffset, Color.LIGHT_GRAY, true); 
                }
            }
            yOffset += ROW_HEIGHT;
        }
    }
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


    
    private int getInt(Map<String, Object> m, String key) {
        Object val = m.get(key);
        if (val instanceof Integer) return (Integer) val;
        return 0;
    }

    private void drawBar(Graphics2D g2, int startVal, int endVal, int y, Color c, boolean isGhost) {
        int visualStart = Math.max(startVal, windowStartInstance);
        int visualEnd = Math.min(endVal, maxTime);
        
        if (visualEnd <= visualStart) return; 

        int x = LABEL_WIDTH + ((visualStart - windowStartInstance) * PIXELS_PER_INSTANCIA);
        int w = (visualEnd - visualStart) * PIXELS_PER_INSTANCIA;
        
        if (isGhost) {
            g2.setColor(new Color(245, 245, 245)); 
            g2.fillRect(x, y, w, ROW_HEIGHT - 8);
            g2.setColor(Color.LIGHT_GRAY); 
            g2.drawRect(x, y, w, ROW_HEIGHT - 8);
        } else {
            g2.setColor(c);
            g2.fillRect(x, y, w, ROW_HEIGHT - 8); // Altura normal 14px
            //g2.setColor(c.darker());
           // g2.drawRect(x, y, w, ROW_HEIGHT - 8);
        }
    }

    private void drawTimeline(Graphics g2) {
        g2.setFont(new Font("Arial", Font.PLAIN, 10));
        g2.setColor(Color.DARK_GRAY);
        
        for (int i = windowStartInstance; i <= maxTime; i++) {
            int x = LABEL_WIDTH + ((i - windowStartInstance) * PIXELS_PER_INSTANCIA);
            g2.setColor(new Color(230, 230, 230));
            g2.drawLine(x, HEADER_HEIGHT - 5, x, getHeight());

            g2.setColor(Color.BLACK);
            String label;
            if (modoCiclico) {
                int val = ((i - 1) % 25) + 1; 
                if (i == 0) val = 0;
                label = String.valueOf(val);
            } else {
                label = String.valueOf(i);
            }
            g2.drawString(label, x - 3, HEADER_HEIGHT - 10);
        }
    }
    
    private Color getProcessColor(String name) {
        int index = processRowMap.getOrDefault(name, 0);
        return PROCESS_COLORS[index % PROCESS_COLORS.length];
    }
}