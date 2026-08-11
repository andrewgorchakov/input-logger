package ru.andrewgorchakov.inputlogger;

import static org.lwjgl.glfw.GLFW.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

public class ParallelLogger {
    private static long window;
    private final InputEventsLog log = new InputEventsLog();
    private final ObjectMapper mapper = new ObjectMapper();

    private static final double MOUSE_MOVE_THRESHOLD = 6.0; // порог чувствительности мыши
    private double lastLoggedX = Double.NaN;
    private double lastLoggedY = Double.NaN;

    public void start() {
        if (!glfwInit()) {
            throw new RuntimeException("GLFW init failed");
        }

        window = glfwCreateWindow(1280, 720, "Parallel Logger (recording)", 0, 0);
        if (window == 0) {
            throw new RuntimeException("Failed to create GLFW window");
        }
        glfwMakeContextCurrent(window);

        setupCallbacks();

        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
        }

        saveLog();

        glfwDestroyWindow(window);
        glfwTerminate();
    }

    // устанавливаем колбэки
    private void setupCallbacks() {
        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            String id = getKeyIdAsCode(key);
            long timeNs = System.currentTimeMillis();

            if (action == GLFW_PRESS) {
                log.press(id, timeNs);
            } else if (action == GLFW_RELEASE) {
                log.release(id, timeNs);
            }
        });

        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            String id = getMouseId(button);
            long timeNs = System.currentTimeMillis();
            if (action == GLFW_PRESS) {
                log.press(id, timeNs);
            } else if (action == GLFW_RELEASE) {
                log.release(id, timeNs);
            }
        });

        glfwSetCursorPosCallback(window, (w, x, y) -> {
            if (Double.isNaN(lastLoggedX)) {
                lastLoggedX = x;
                lastLoggedY = y;
                return;
            }

            double dx = Math.abs(x - lastLoggedX);
            double dy = Math.abs(y - lastLoggedY);

            if (dx > MOUSE_MOVE_THRESHOLD || dy > MOUSE_MOVE_THRESHOLD) {
                log.move((int) x, (int) y, System.currentTimeMillis());
                lastLoggedX = x;
                lastLoggedY = y;
            }
        });
    }

    // получаем значения нажимаемых клавиш по их константам GLFW
    private String getKeyId(int key) {
        switch (key) {
            case GLFW_KEY_W:
                return "W";
            case GLFW_KEY_A:
                return "A";
            case GLFW_KEY_S:
                return "S";
            case GLFW_KEY_D:
                return "D";
            case GLFW_KEY_SPACE:
                return "SPACE";
            case GLFW_KEY_LEFT_SHIFT:
                return "SHIFT_L";
            default:
                return "KEY_" + key;
        }
    }

    // получаем значения нажимаемых клавиш в виде кода по их константам GLFW
    private String getKeyIdAsCode(int key) {
        switch (key) {
            case GLFW_KEY_W:
                return "87";
            case GLFW_KEY_A:
                return "65";
            case GLFW_KEY_S:
                return "83";
            case GLFW_KEY_D:
                return "68";
            case GLFW_KEY_SPACE:
                return "32";
            case GLFW_KEY_LEFT_SHIFT:
                return "340";
            default:
                return "KEY_" + key;
        }
    }

    // получаем значения нажимаемых клавиш мыши по их константам GLFW
    private String getMouseId(int button) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            return "MOUSE_L";
        } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            return "MOUSE_R";
        } else if (button == GLFW_MOUSE_BUTTON_MIDDLE) {
            return "MOUSE_M";
        } else {
            return "MOUSE_" + button;
        }
    }

    // сохраняем лог в файл
    private void saveLog() {
        try {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File("parallel_input_log.json"), log);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new ParallelLogger().start();
    }
}