package itt.tcl.download;

public enum ProjectSource {
    MODRINTH("Modrinth"),
    CURSEFORGE("CurseForge");

    private final String displayName;

    ProjectSource(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
