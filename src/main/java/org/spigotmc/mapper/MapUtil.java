package org.spigotmc.mapper;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapUtil
{

    private static final Pattern MEMBER_PATTERN = Pattern.compile( "(?:\\d+:\\d+:)?(.*?) (.*?) \\-> (.*)" );
    //
    private List<String> header = new ArrayList<>();
    private final BiMap<String, String> obf2Buk = HashBiMap.create();

    public void loadBuk(File bukClasses) throws IOException
    {
        for ( String line : Files.readAllLines( bukClasses.toPath() ) )
        {
            if ( line.startsWith( "#" ) )
            {
                header.add( line );
                continue;
            }

            String[] split = line.split( " " );
            if ( split.length == 2 )
            {
                obf2Buk.put( split[0], split[1] );
            }
        }
    }

    public void makeFieldMaps(File mojIn, File fields) throws IOException
    {
        List<String> outFields = new ArrayList<>( header );

        String currentClass = null;
        outer:
        for ( String line : Files.readAllLines( mojIn.toPath() ) )
        {
            if ( line.startsWith( "#" ) )
            {
                continue;
            }
            line = line.trim();

            if ( line.endsWith( ":" ) )
            {
                currentClass = null;

                String[] parts = line.split( " -> " );
                String orig = parts[0].replace( '.', '/' );
                String obf = parts[1].substring( 0, parts[1].length() - 1 ).replace( '.', '/' );

                String buk = deobfClass( obf, obf2Buk );
                if ( buk == null )
                {
                    continue;
                }

                currentClass = buk;
            } else if ( currentClass != null )
            {
                Matcher matcher = MEMBER_PATTERN.matcher( line );
                matcher.find();

                String obf = matcher.group( 3 );
                String nameDesc = matcher.group( 2 );
                if ( !nameDesc.contains( "(" ) )
                {
                    if ( nameDesc.contains( "$" ) )
                    {
                        continue;
                    }
                    if ( obf.equals( "if" ) || obf.equals( "do" ) )
                    {
                        obf += "_";
                    }

                    outFields.add( currentClass + " " + obf + " " + nameDesc );
                }
            }
        }

        Collections.sort( outFields );
        Files.write( fields.toPath(), outFields );
    }

    public void makeCombinedMaps(File out, File... members) throws IOException
    {
        List<String> combined = new ArrayList<>( header );

        for ( Map.Entry<String, String> map : obf2Buk.entrySet() )
        {
            combined.add( map.getKey() + " " + map.getValue() );
        }

        for ( File member : members )
        {
            for ( String line : Files.readAllLines( member.toPath() ) )
            {
                if ( line.startsWith( "#" ) )
                {
                    continue;
                }
                line = line.trim();

                String[] split = line.split( " " );
                if ( split.length == 3 )
                {
                    String clazz = split[0];
                    String orig = split[1];
                    String targ = split[2];

                    combined.add( deobfClass( clazz, obf2Buk.inverse() ) + " " + orig + " " + targ );
                } else if ( split.length == 4 )
                {
                    String clazz = split[0];
                    String orig = split[1];
                    String desc = split[2];
                    String targ = split[3];

                    combined.add( deobfClass( clazz, obf2Buk.inverse() ) + " " + orig + " " + toObf( desc, obf2Buk.inverse() ) + " " + targ );
                }
            }
        }

        Files.write( out.toPath(), combined );
    }

    public static String deobfClass(String obf, Map<String, String> classMaps)
    {
        String buk = classMaps.get( obf );
        if ( buk == null )
        {
            StringBuilder inner = new StringBuilder();

            while ( buk == null )
            {
                int idx = obf.lastIndexOf( '$' );
                if ( idx == -1 )
                {
                    return null;
                }
                inner.insert( 0, obf.substring( idx ) );
                obf = obf.substring( 0, idx );

                buk = classMaps.get( obf );
            }

            buk += inner;
        }
        return buk;
    }

    public static String toObf(String desc, Map<String, String> map)
    {
        desc = desc.substring( 1 );
        StringBuilder out = new StringBuilder( "(" );
        if ( desc.charAt( 0 ) == ')' )
        {
            desc = desc.substring( 1 );
            out.append( ')' );
        }
        while ( desc.length() > 0 )
        {
            desc = obfType( desc, map, out );
            if ( desc.length() > 0 && desc.charAt( 0 ) == ')' )
            {
                desc = desc.substring( 1 );
                out.append( ')' );
            }
        }
        return out.toString();
    }

    public static String obfType(String desc, Map<String, String> map, StringBuilder out)
    {
        int size = 1;
        switch ( desc.charAt( 0 ) )
        {
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'I':
            case 'J':
            case 'S':
            case 'Z':
            case 'V':
                out.append( desc.charAt( 0 ) );
                break;
            case '[':
                out.append( "[" );
                return obfType( desc.substring( 1 ), map, out );
            case 'L':
                String type = desc.substring( 1, desc.indexOf( ";" ) );
                size += type.length() + 1;
                out.append( "L" ).append( map.containsKey( type ) ? map.get( type ) : type ).append( ";" );
        }
        return desc.substring( size );
    }
}
