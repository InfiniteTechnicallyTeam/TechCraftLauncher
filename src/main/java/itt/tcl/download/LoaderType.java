package itt.tcl.download;

public enum LoaderType {
    VANILLA("vanilla", "Vanilla"),
    FORGE("forge", "Forge"),
    NEOFORGE("neoforge", "NeoForge"),
    FABRIC("fabric", "Fabric"),
    QUILT("quilt", "Quilt");

    private final String id;
    private final String displayName;

    LoaderType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean supportsOptiFine() {
        return this == VANILLA || this == FORGE;
    }

    public boolean supportsMods() {
        return this != VANILLA;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
