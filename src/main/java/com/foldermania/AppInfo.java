package com.foldermania;

public final class AppInfo {

    public static final String NAME = "Folder Mania";
    /** Read from the jar manifest (set by Gradle from the release tag); "dev" when run from source. */
    public static final String VERSION = java.util.Objects.requireNonNullElse(
            AppInfo.class.getPackage().getImplementationVersion(), "dev");
    public static final String REPO_URL = "https://github.com/LytTheBit/FolderMania";

    private AppInfo() {}
}
