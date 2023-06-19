package org.spigotmc.builder;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BuildInfo
{

    public static BuildInfo DEV = new BuildInfo( "dev", "Development", 0, null, new BuildInfo.Refs( "master", "master", "master", "master" ) );
    public static BuildInfo EXPERIMENTAL = new BuildInfo( "exp", "Experimental", 0, null, new BuildInfo.Refs( "origin/experimental", "origin/experimental", "origin/experimental", "origin/experimental" ) );
    //
    private String name;
    private String description;
    private int toolsVersion = -1;
    private int[] javaVersions;
    private Refs refs;

    @Data
    @AllArgsConstructor
    public static class Refs
    {

        private String BuildData;
        private String Bukkit;
        private String CraftBukkit;
        private String Spigot;
    }
}
