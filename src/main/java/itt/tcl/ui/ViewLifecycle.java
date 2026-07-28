package itt.tcl.ui;

public interface ViewLifecycle {
    default void onViewShown() {}

    default void onViewHidden() {}
}
