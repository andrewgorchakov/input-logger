package ru.andrewgorchakov.inputlogger;

import static org.lwjgl.glfw.GLFW.*;
import org.lwjgl.opengl.*;
import static org.lwjgl.opengl.GL11.*;
import com.fasterxml.jackson.databind.*;

import java.nio.file.*;
import java.util.*;

public class VisualPlayer {
    private static long window;
    private final InputEventsLog log;

    private long baseTimeNs;
    private double gameTimeSec = 0.0;
    private long lastFrameTimeNs;
    private boolean logFinished = false;

    private int width = 1280;
    private int height = 720;

    private double rawX;
    private double rawY;
    private double mouseX = width / 2.0;
    private double mouseY = height / 2.0;
    private double mouseXTgt = mouseX;
    private double mouseYTgt = mouseY;
    private long mouseLastEventTimeNs = -1;

    private double timeDimensionScale = 1000.0;


    // Границы из лога
    private double logMinX, logMaxX, logMinY, logMaxY;

    public VisualPlayer(String pathStr) throws Exception {
        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) {
            throw new java.io.FileNotFoundException("Файл лога не найден: " + path.toAbsolutePath());
        }
        ObjectMapper mapper = new ObjectMapper();
        // читаем и распарсиваем лог
        this.log = mapper.readValue(path.toFile(), InputEventsLog.class);

        // Вычисляем реальный разброс координат в логе
        logMinX = Double.MAX_VALUE; logMaxX = Double.MIN_VALUE;
        logMinY = Double.MAX_VALUE; logMaxY = Double.MIN_VALUE;

        for (InputEventsLog.MousePoint p : log.getMousePoints()) {
            logMinX = Math.min(logMinX, p.getX());
            logMaxX = Math.max(logMaxX, p.getX());
            logMinY = Math.min(logMinY, p.getY());
            logMaxY = Math.max(logMaxY, p.getY());
        }

        if (logMaxX == logMinX) { logMaxX += 1.0; }
        if (logMaxY == logMinY) { logMaxY += 1.0; }

        System.out.println("Окно плеера: " + width + "x" + height);
    }

    public void play(double speedMultiplier) {
        if (!glfwInit()) throw new RuntimeException("GLFW init failed");

        window = glfwCreateWindow(width, height, "Input Log Player", 0, 0);
        if (window == 0) throw new RuntimeException("Failed to create GLFW window");
        glfwMakeContextCurrent(window);
        glfwShowWindow(window);
        GL.createCapabilities();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // создаём таймлайн (лист для хранения TimelineEvent)
        List<TimelineEvent> timeline = new ArrayList<>();

        // Обрабатываем лог по событиям клавиатуры
        for (Map.Entry<String, List<InputEventsLog.TimeInterval>> entry : log.getKeyChannels().entrySet()) {
            String channel = entry.getKey();
            for (InputEventsLog.TimeInterval iv : entry.getValue()) {
                timeline.add(new TimelineEvent(iv.getStartNs(), "PRESS", channel));
                if (iv.getEndNs() != -1) {
                    timeline.add(new TimelineEvent(iv.getEndNs(), "RELEASE", channel));
                }
            }
        }

        // Обрабатываем лог по событиям мыши
        for (InputEventsLog.MousePoint p : log.getMousePoints()) {
            String coords = String.format("%.1f,%.1f", p.getX(), p.getY());
            timeline.add(new TimelineEvent(p.getTimestampNs(), "MOVE", coords));
        }

        // делаем сортировку таймлайна
        timeline.sort(Comparator.comparingLong(TimelineEvent::getTimeNs));

        baseTimeNs = timeline.isEmpty() ? System.currentTimeMillis() : timeline.get(0).getTimeNs();
        long lastEventTimeNs = timeline.isEmpty() ? baseTimeNs : timeline.get(timeline.size() - 1).getTimeNs();

        lastFrameTimeNs = System.currentTimeMillis();

        double logHeight = logMaxY - logMinY;
        double offsetY = (height - logHeight) / 2.0;  // центрируем движение по вертикали
        double scaleY = 1.0;                         // масштаб 1:1 (без растягивания)

        // основной цикл воспроизведения
        while (!glfwWindowShouldClose(window)) {
            long nowNs = System.currentTimeMillis();
            double deltaSec = (nowNs - lastFrameTimeNs) / timeDimensionScale * speedMultiplier;
            lastFrameTimeNs = nowNs;
            gameTimeSec += deltaSec;

            glClear(GL_COLOR_BUFFER_BIT);
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, width, 0, height, -1, 1);
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();

            Set<String> activeKeys = new HashSet<>();

            boolean showClick = false;
            long clickEndTime = 0;

            if (!logFinished) {
                for (TimelineEvent e : timeline) {
                    long offsetNs = e.getTimeNs() - baseTimeNs;
                    double eventTimeSec = offsetNs / timeDimensionScale;

                    if (eventTimeSec > gameTimeSec) break;

                    switch (e.getType()) {
                        case "PRESS":
                            activeKeys.add(e.getChannel());
                            if (e.getChannel().startsWith("MOUSE_")) {
                                showClick = true;
                                clickEndTime = e.getTimeNs() + 200L;
                            }
                            break;
                        case "RELEASE":
                            activeKeys.remove(e.getChannel());
                            break;
                        case "MOVE":
                            String[] parts = e.getChannel().split(",");
                            rawX = Double.parseDouble(parts[0]);
                            rawY = Double.parseDouble(parts[2]);

                            // Масштабирование X: растягиваем на всю ширину
                            mouseXTgt = ((rawX - logMinX) / (logMaxX - logMinX)) * width;

                            // Масштабирование Y: сохраняем пропорции, центрируем, инвертируем
                            double yInWindow = ((rawY - logMinY) * scaleY) + offsetY;
                            mouseYTgt = height - yInWindow;

                            mouseLastEventTimeNs = e.getTimeNs();
                            break;
                    }
                }

                String line = String.format("\rКоординаты мыши: X=%.1f  Y=%.1f       ", rawX, rawY);
                System.out.print(line);

                long lastEventOffsetNs = lastEventTimeNs - baseTimeNs;
                double lastEventTimeSec = lastEventOffsetNs / timeDimensionScale;
                if (gameTimeSec >= lastEventTimeSec) {
                    logFinished = true;
                    System.out.println("\n=== ЛОГ ЗАВЕРШЁН ===");
                }
            }

            // Плавная интерполяция
            if (mouseLastEventTimeNs != -1) {
                long elapsedNs = nowNs - mouseLastEventTimeNs;
                double elapsedSec = elapsedNs / timeDimensionScale;
                double durationSec = 0.05;

                if (elapsedSec >= durationSec) {
                    mouseX = mouseXTgt;
                    mouseY = mouseYTgt;
                } else {
                    double t = elapsedSec / durationSec;
                    mouseX += (mouseXTgt - mouseX) * t;
                    mouseY += (mouseYTgt - mouseY) * t;
                }
            }

            boolean isClickActive = showClick && (nowNs < clickEndTime);

            // отображаем те кнопки как нажатые, которые находятся в данный момент в activeKeys
            drawKeyButtons(activeKeys);
            drawMouse(mouseX, mouseY, isClickActive);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }

        glfwDestroyWindow(window);
        glfwTerminate();
    }

    // рисуем кнопки
    private void drawKeyButtons(Set<String> active) {
        int startX = 460;          // левая граница колонки ASD
        int yTop = 300;            // Y для верхней кнопки (W)
        int gapY = 80;             // вертикальный отступ между кнопками

        int wKey = 60, hKey = 60;   // размер кнопок WASD
        int wSpace = 260, hSpace = 40;
        int wShift = 140, hShift = 40;
        int wMouse = 80, hMouse = 40;

        // WASD
        drawButton(startX + 70, yTop + 150, wKey, hKey, active.contains("W"));
        drawButton(startX, yTop + gapY, wKey, hKey, active.contains("A"));
        drawButton(startX + 70, yTop + gapY, wKey, hKey, active.contains("S"));
        drawButton(startX + 140, yTop + gapY, wKey, hKey, active.contains("D"));

        // Пробел
        drawButton(startX, yTop + 2 * gapY - 160, wSpace, hSpace, active.contains("SPACE"));

        // Shift
        drawButton(startX - 160, yTop + 2 * gapY - 160, wShift, hShift, active.contains("SHIFT_L"));

        // Кнопки мыши
        int mouseX = startX + wSpace + 20;
        int mouseY = yTop + 2 * gapY + 10;
        drawButton(mouseX, mouseY, wMouse, hMouse, active.contains("MOUSE_L"));
        drawButton(mouseX + wMouse + 20, mouseY, wMouse, hMouse, active.contains("MOUSE_R"));
    }

    // метод рисует одну кнопку
    private void drawButton(int x, int y, int w, int h, boolean isActive) {
        setColor(0.4f, 0.4f, 0.4f);
        drawRect(x, y, w, h);
        setColor(isActive ? 0.0f : 0.2f, isActive ? 1.0f : 0.3f, isActive ? 0.0f : 0.1f);
        drawRect(x + 2, y + 2, w - 4, h - 4);
    }

    // метод рисует курсор мыши
    private void drawMouse(double mx, double my, boolean isClick) {
        setColor(1.0f, 1.0f, 1.0f);
        drawCircle((float) mx, (float) my, 10);
        if (isClick) {
            setColor(1.0f, 0.0f, 0.0f);
            drawCircle((float) mx, (float) my, 25);
        }
    }

    private void setColor(float r, float g, float b) { glColor3f(r, g, b); }

    private void drawRect(int x, int y, int w, int h) {
        glBegin(GL_QUADS);
        glVertex2i(x, y); glVertex2i(x + w, y);
        glVertex2i(x + w, y + h); glVertex2i(x, y + h);
        glEnd();
    }

    private void drawCircle(float cx, float cy, float r) {
        int segments = 32;
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(cx, cy);
        for (int i = 0; i <= segments; i++) {
            float theta = (float) (2.0 * Math.PI * i / segments);
            float x = cx + r * (float) Math.cos(theta);
            float y = cy + r * (float) Math.sin(theta);
            glVertex2f(x, y);
        }
        glEnd();
    }

    public static void main(String[] args) {
        try {
            String path = (args.length > 0) ? args[0] : "parallel_input_log.json";
            double speed = (args.length > 1) ? Double.parseDouble(args[1]) : 1.0;
            new VisualPlayer(path).play(speed);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}