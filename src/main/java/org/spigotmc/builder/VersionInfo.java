package org.spigotmc.builder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VersionInfo
{

    private String minecraftVersion;
    private String accessTransforms;
    private String classMappings;
    private String memberMappings;
    private String packageMappings;
    private String minecraftHash;
    private String classMapCommand;
    private String memberMapCommand;
    private String finalMapCommand;
    private String decompileCommand;
    private String serverUrl;
    private String mappingsUrl;
    private String spigotVersion;
    private int toolsVersion = -1;

    public VersionInfo(String minecraftVersion, String accessTransforms, String classMappings, String memberMappings, String packageMappings, String minecraftHash)
    {
        this.minecraftVersion = minecraftVersion;
        this.accessTransforms = accessTransforms;
        this.classMappings = classMappings;
        this.memberMappings = memberMappings;
        this.packageMappings = packageMappings;
        this.minecraftHash = minecraftHash;
    }

    public VersionInfo(String minecraftVersion, String accessTransforms, String classMappings, String memberMappings, String packageMappings, String minecraftHash, String decompileCommand)
    {
        this.minecraftVersion = minecraftVersion;
        this.accessTransforms = accessTransforms;
        this.classMappings = classMappings;
        this.memberMappings = memberMappings;
        this.packageMappings = packageMappings;
        this.minecraftHash = minecraftHash;
        this.decompileCommand = decompileCommand;
    }

    public String getShaServerHash()
    {
        return hashFromUrl( serverUrl );
    }

    public String getShaMappingsHash()
    {
        return hashFromUrl( mappingsUrl );
    }
    private static final Pattern URL_PATTERN = Pattern.compile( "https://(?:launcher|piston-data).mojang.com/v1/objects/([\\da-f]{40})/.*" );

    public static String hashFromUrl(String url)
    {
        if ( url == null )
        {
            return null;
        }

        Matcher match = URL_PATTERN.matcher( url );
        return ( match.find() ) ? match.group( 1 ) : null;
    }
}
