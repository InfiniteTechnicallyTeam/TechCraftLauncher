package itt.tcl.ui;

import javafx.geometry.Pos;
import javafx.beans.value.ChangeListener;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Map;
import java.util.WeakHashMap;

public final class WindowChrome {
    private static final double TITLE_BAR_HEIGHT = 40;
    private static final double RESIZE_MARGIN = 6;
    private static final boolean CUSTOM_CHROME_ENABLED =
            System.getProperty("os.name", "").toLowerCase().startsWith("win");
    private static final Map<Stage, ChangeListener<Boolean>> MAXIMIZE_LISTENERS =
            new WeakHashMap<>();
    private static final Map<Stage, Label> TITLE_LABELS =
            new WeakHashMap<>();
    private static final Map<Stage, ChromeButtons> CHROME_BUTTONS =
            new WeakHashMap<>();

    private WindowChrome() {}

    public static void configureStage(Stage stage) {
        if (CUSTOM_CHROME_ENABLED) {
            stage.initStyle(StageStyle.UNDECORATED);
        }
    }

    public static double additionalHeight() {
        return CUSTOM_CHROME_ENABLED ? TITLE_BAR_HEIGHT : 0;
    }

    public static Parent wrap(Stage stage, Parent content, String title) {
        if (!CUSTOM_CHROME_ENABLED) {
            return content;
        }

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("window-title");
        TITLE_LABELS.put(stage, titleLabel);

        HBox dragArea = new HBox(titleLabel);
        dragArea.setAlignment(Pos.CENTER_LEFT);
        dragArea.getStyleClass().add("window-drag-area");
        HBox.setHgrow(dragArea, Priority.ALWAYS);

        Button minimizeButton = createWindowButton(
                "—", "window-minimize-button", LanguageManager.text("window.minimize")
        );
        Button maximizeButton = createWindowButton(
                "□", "window-maximize-button", LanguageManager.text("window.maximize")
        );
        Button closeButton = createWindowButton(
                "×", "window-close-button", LanguageManager.text("window.close")
        );
        CHROME_BUTTONS.put(
                stage,
                new ChromeButtons(minimizeButton, maximizeButton, closeButton)
        );

        minimizeButton.setOnAction(event -> stage.setIconified(true));
        maximizeButton.setOnAction(event -> toggleMaximized(stage));
        closeButton.setOnAction(event -> stage.close());

        installMaximizeButtonListener(stage, maximizeButton);
        updateMaximizeButton(maximizeButton, stage.isMaximized());

        HBox windowButtons = new HBox(minimizeButton, maximizeButton, closeButton);
        windowButtons.setAlignment(Pos.CENTER_RIGHT);
        windowButtons.getStyleClass().add("window-buttons");

        HBox titleBar = new HBox(dragArea, windowButtons);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setMinHeight(TITLE_BAR_HEIGHT);
        titleBar.setPrefHeight(TITLE_BAR_HEIGHT);
        titleBar.setMaxHeight(TITLE_BAR_HEIGHT);
        titleBar.getStyleClass().add("window-title-bar");

        VBox frame = new VBox(titleBar, content);
        frame.getStyleClass().add("window-frame");
        VBox.setVgrow(content, Priority.ALWAYS);

        installWindowDragging(stage, dragArea);
        installWindowResizing(stage, frame);
        return frame;
    }

    public static void updateTitle(Stage stage, String title) {
        stage.setTitle(title);
        Label titleLabel = TITLE_LABELS.get(stage);
        if (titleLabel != null) {
            titleLabel.setText(title);
        }
        ChromeButtons buttons = CHROME_BUTTONS.get(stage);
        if (buttons != null) {
            updateButtonLabel(
                    buttons.minimize(),
                    LanguageManager.text("window.minimize")
            );
            updateMaximizeButton(buttons.maximize(), stage.isMaximized());
            updateButtonLabel(
                    buttons.close(),
                    LanguageManager.text("window.close")
            );
        }
    }

    private static void updateButtonLabel(Button button, String label) {
        button.setAccessibleText(label);
        button.getTooltip().setText(label);
    }

    private static Button createWindowButton(String symbol, String styleClass, String tooltipText) {
        Button button = new Button(symbol);
        button.setFocusTraversable(false);
        button.setMnemonicParsing(false);
        button.setAccessibleText(tooltipText);
        button.setTooltip(new Tooltip(tooltipText));
        button.getStyleClass().addAll("window-button", styleClass);
        return button;
    }

    private static void updateMaximizeButton(Button button, boolean maximized) {
        String label = LanguageManager.text(maximized ? "window.restore" : "window.maximize");
        button.setText(maximized ? "❐" : "□");
        button.setAccessibleText(label);
        button.getTooltip().setText(label);
    }

    private static void installMaximizeButtonListener(Stage stage, Button button) {
        ChangeListener<Boolean> oldListener = MAXIMIZE_LISTENERS.remove(stage);
        if (oldListener != null) {
            stage.maximizedProperty().removeListener(oldListener);
        }

        ChangeListener<Boolean> listener =
                (observable, wasMaximized, isMaximized) ->
                        updateMaximizeButton(button, isMaximized);
        MAXIMIZE_LISTENERS.put(stage, listener);
        stage.maximizedProperty().addListener(listener);
    }

    private static void toggleMaximized(Stage stage) {
        stage.setMaximized(!stage.isMaximized());
    }

    private static void installWindowDragging(Stage stage, HBox dragArea) {
        double[] dragOffset = new double[2];

        dragArea.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !stage.isMaximized()) {
                dragOffset[0] = event.getScreenX() - stage.getX();
                dragOffset[1] = event.getScreenY() - stage.getY();
            }
        });

        dragArea.setOnMouseDragged(event -> {
            if (event.isPrimaryButtonDown() && !stage.isMaximized()) {
                stage.setX(event.getScreenX() - dragOffset[0]);
                stage.setY(event.getScreenY() - dragOffset[1]);
            }
        });

        dragArea.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                toggleMaximized(stage);
            }
        });
    }

    private static void installWindowResizing(Stage stage, Parent frame) {
        ResizeState state = new ResizeState();

        frame.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            if (state.direction == ResizeDirection.NONE) {
                ResizeDirection direction = findResizeDirection(stage, frame, event);
                frame.setCursor(direction.cursor);
            }
        });

        frame.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
            if (state.direction == ResizeDirection.NONE) {
                frame.setCursor(Cursor.DEFAULT);
            }
        });

        frame.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY || stage.isMaximized()) {
                return;
            }

            state.direction = findResizeDirection(stage, frame, event);
            if (state.direction == ResizeDirection.NONE) {
                return;
            }

            state.screenX = event.getScreenX();
            state.screenY = event.getScreenY();
            state.stageX = stage.getX();
            state.stageY = stage.getY();
            state.stageWidth = stage.getWidth();
            state.stageHeight = stage.getHeight();
            event.consume();
        });

        frame.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (state.direction == ResizeDirection.NONE) {
                return;
            }
            resizeStage(stage, state, event.getScreenX(), event.getScreenY());
            event.consume();
        });

        frame.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (state.direction != ResizeDirection.NONE) {
                state.direction = ResizeDirection.NONE;
                frame.setCursor(Cursor.DEFAULT);
                event.consume();
            }
        });
    }

    private static ResizeDirection findResizeDirection(
            Stage stage,
            Parent frame,
            MouseEvent event
    ) {
        if (!stage.isResizable() || stage.isMaximized()) {
            return ResizeDirection.NONE;
        }

        double x = event.getSceneX();
        double y = event.getSceneY();
        double width = frame.getScene().getWidth();
        double height = frame.getScene().getHeight();

        boolean left = x <= RESIZE_MARGIN;
        boolean right = x >= width - RESIZE_MARGIN;
        boolean top = y <= RESIZE_MARGIN;
        boolean bottom = y >= height - RESIZE_MARGIN;

        if (top && left) return ResizeDirection.NORTH_WEST;
        if (top && right) return ResizeDirection.NORTH_EAST;
        if (bottom && left) return ResizeDirection.SOUTH_WEST;
        if (bottom && right) return ResizeDirection.SOUTH_EAST;
        if (left) return ResizeDirection.WEST;
        if (right) return ResizeDirection.EAST;
        if (top) return ResizeDirection.NORTH;
        if (bottom) return ResizeDirection.SOUTH;
        return ResizeDirection.NONE;
    }

    private static void resizeStage(
            Stage stage,
            ResizeState state,
            double screenX,
            double screenY
    ) {
        double deltaX = screenX - state.screenX;
        double deltaY = screenY - state.screenY;

        if (state.direction.resizesLeft) {
            double width = Math.max(stage.getMinWidth(), state.stageWidth - deltaX);
            stage.setX(state.stageX + state.stageWidth - width);
            stage.setWidth(width);
        } else if (state.direction.resizesRight) {
            stage.setWidth(Math.max(stage.getMinWidth(), state.stageWidth + deltaX));
        }

        if (state.direction.resizesTop) {
            double height = Math.max(stage.getMinHeight(), state.stageHeight - deltaY);
            stage.setY(state.stageY + state.stageHeight - height);
            stage.setHeight(height);
        } else if (state.direction.resizesBottom) {
            stage.setHeight(Math.max(stage.getMinHeight(), state.stageHeight + deltaY));
        }
    }

    private static final class ResizeState {
        private ResizeDirection direction = ResizeDirection.NONE;
        private double screenX;
        private double screenY;
        private double stageX;
        private double stageY;
        private double stageWidth;
        private double stageHeight;
    }

    private record ChromeButtons(
            Button minimize,
            Button maximize,
            Button close
    ) {}

    private enum ResizeDirection {
        NONE(Cursor.DEFAULT, false, false, false, false),
        NORTH(Cursor.N_RESIZE, false, false, true, false),
        NORTH_EAST(Cursor.NE_RESIZE, false, true, true, false),
        EAST(Cursor.E_RESIZE, false, true, false, false),
        SOUTH_EAST(Cursor.SE_RESIZE, false, true, false, true),
        SOUTH(Cursor.S_RESIZE, false, false, false, true),
        SOUTH_WEST(Cursor.SW_RESIZE, true, false, false, true),
        WEST(Cursor.W_RESIZE, true, false, false, false),
        NORTH_WEST(Cursor.NW_RESIZE, true, false, true, false);

        private final Cursor cursor;
        private final boolean resizesLeft;
        private final boolean resizesRight;
        private final boolean resizesTop;
        private final boolean resizesBottom;

        ResizeDirection(
                Cursor cursor,
                boolean resizesLeft,
                boolean resizesRight,
                boolean resizesTop,
                boolean resizesBottom
        ) {
            this.cursor = cursor;
            this.resizesLeft = resizesLeft;
            this.resizesRight = resizesRight;
            this.resizesTop = resizesTop;
            this.resizesBottom = resizesBottom;
        }
    }
}
