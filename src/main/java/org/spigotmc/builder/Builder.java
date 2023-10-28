package org.spigotmc.builder;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.ObjectArrays;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import difflib.DiffUtils;
import difflib.Patch;
import java.awt.Desktop;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.JFrame;
import javax.swing.JLabel;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.EnumConverter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.spigotmc.mapper.MapUtil;

public class Builder
{

    public static final String LOG_FILE = "BuildTools.log.txt";
    public static final boolean IS_WINDOWS = System.getProperty( "os.name" ).startsWith( "Windows" );
    public static final File CWD = new File( "." );
    private static final boolean autocrlf = !"\n".equals( System.getProperty( "line.separator" ) );
    private static boolean dontUpdate;
    private static List<Compile> compile;
    private static boolean generateSource;
    private static boolean generateDocs;
    private static boolean dev;
    private static boolean remapped;
    private static List<PullRequest> pullRequests;
    private static String applyPatchesShell = "sh";
    private static boolean didClone = false;
    //
    private static BuildInfo buildInfo = BuildInfo.DEV;
    //
    private static File msysDir;
    private static File maven;
    private static boolean proxyAvailable = false;
    private static String proxyAddress = "";
    private static int proxyPort = 0;
    private static BuilderProxySelector proxySelector = null;

    public static void main(String[] args) throws Exception
    {
        logOutput();

        // May be null
        String buildVersion = Builder.class.getPackage().getImplementationVersion();
        int buildNumber = -1;
        if ( buildVersion != null )
        {
            String[] split = buildVersion.split( "-" );
            if ( split.length == 4 )
            {
                try
                {
                    buildNumber = Integer.parseInt( split[3] );
                } catch ( NumberFormatException ex )
                {
                }
            }
        }

        System.out.println( "Loading BuildTools version: " + buildVersion + " (#" + buildNumber + ")" );
        System.out.println( "Java Version: " + JavaVersion.getCurrentVersion() );
        System.out.println( "Current Path: " + CWD.getAbsolutePath() );

        if ( CWD.getAbsolutePath().contains( "'" ) || CWD.getAbsolutePath().contains( "#" ) || CWD.getAbsolutePath().contains( "~" ) || CWD.getAbsolutePath().contains( "(" ) || CWD.getAbsolutePath().contains( ")" ) )
        {
            System.err.println( "Please do not run in a path with special characters!" );
            return;
        }

        if ( CWD.getAbsolutePath().contains( "Dropbox" ) || CWD.getAbsolutePath().contains( "OneDrive" ) )
        {
            System.err.println( "Please do not run BuildTools in a Dropbox, OneDrive, or similar. You can always copy the completed jars there later." );
            return;
        }

        if ( false && System.console() == null )
        {
            JFrame jFrame = new JFrame();
            jFrame.setTitle( "SpigotMC - BuildTools" );
            jFrame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
            jFrame.getContentPane().add( new JLabel( "You have to run BuildTools through bash (msysgit). Please read our wiki." ) );
            jFrame.pack();
            jFrame.setVisible( true );

            Desktop.getDesktop().browse( new URI( "https://www.spigotmc.org/wiki/buildtools/" ) );
            return;
        }

        OptionParser parser = new OptionParser();
        OptionSpec<Void> help = parser.accepts( "help", "Show the help" );
        OptionSpec<Void> disableCertFlag = parser.accepts( "disable-certificate-check", "Disable HTTPS certificate check" );
        OptionSpec<Void> disableJavaCheck = parser.accepts( "disable-java-check", "Disable Java version check" );
        OptionSpec<Void> dontUpdateFlag = parser.accepts( "dont-update", "Don't pull updates from Git" );
        OptionSpec<Void> skipCompileFlag = parser.accepts( "skip-compile", "Skip compilation" );
        OptionSpec<Void> generateSourceFlag = parser.accepts( "generate-source", "Generate source jar" );
        OptionSpec<Void> generateDocsFlag = parser.accepts( "generate-docs", "Generate Javadoc jar" );
        OptionSpec<Void> devFlag = parser.accepts( "dev", "Development mode" );
        OptionSpec<Void> experimentalFlag = parser.accepts( "experimental", "Build experimental version" );
        OptionSpec<Void> remappedFlag = parser.accepts( "remapped", "Produce and install extra remapped jars" );
        OptionSpec<File> outputDir = parser.acceptsAll( Arrays.asList( "o", "output-dir" ), "Final jar output directory" ).withRequiredArg().ofType( File.class ).defaultsTo( CWD );
        OptionSpec<String> jenkinsVersion = parser.accepts( "rev", "Version to build" ).withRequiredArg().defaultsTo( "latest" );
        OptionSpec<Compile> toCompile = parser.accepts( "compile", "Software to compile" ).withRequiredArg().ofType( Compile.class ).withValuesConvertedBy( new EnumConverter<Compile>( Compile.class )
        {
        } ).withValuesSeparatedBy( ',' );
        OptionSpec<Void> compileIfChanged = parser.accepts( "compile-if-changed", "Run BuildTools only when changes are detected in the repository" );
        OptionSpec<PullRequest> buildPullRequest = parser.acceptsAll( Arrays.asList( "pull-request", "pr" ), "Build specific pull requests" ).withOptionalArg().withValuesConvertedBy( new PullRequest.PullRequestConverter() );
        OptionSpec<Void> proxy = parser.accepts( "proxy", "Declare the program to use a proxy specified by --proxy-addr and --proxy-port" );
        OptionSpec<String> proxyAddress = parser.accepts( "proxy-addr", "The address of the proxy, either an IP or a domain name" ).withRequiredArg();
        OptionSpec<String> proxyPort = parser.accepts( "proxy-port", "The port of the proxy, must be an integer within the closed interval from 0 to 65535" ).withRequiredArg();

        OptionSet options = parser.parse( args );

        if (options.has(proxy)) {
            if (!options.has(proxyAddress) || !options.has(proxyPort)) {
                System.err.println( "--proxy must be used with both --proxy-addr and --proxy-port, exiting." );
                System.exit(1);
            }
            Builder.proxyAddress = options.valueOf(proxyAddress);
            Builder.proxyPort = Integer.parseInt(options.valueOf(proxyPort));
            Builder.proxySelector = new BuilderProxySelector(Builder.proxyAddress, Builder.proxyPort);
            Builder.proxyAvailable = true;
            ProxySelector.setDefault(Builder.proxySelector);
        }
        if ( options.has( help ) )
        {
            parser.printHelpOn( System.out );
            System.exit( 0 );
        }
        if ( options.has( disableCertFlag ) )
        {
            disableHttpsCertificateCheck();
        }
        dontUpdate = options.has( dontUpdateFlag );
        generateSource = options.has( generateSourceFlag );
        generateDocs = options.has( generateDocsFlag );
        dev = options.has( devFlag );
        // Experimental implies dev but with different refs
        if ( options.has( experimentalFlag ) )
        {
            dev = true;
            buildInfo = BuildInfo.EXPERIMENTAL;
        }
        remapped = options.has( remappedFlag );
        compile = options.valuesOf( toCompile );
        pullRequests = options.valuesOf( buildPullRequest );
        validatedPullRequestsOptions();
        if ( options.has( skipCompileFlag ) )
        {
            compile = Collections.singletonList( Compile.NONE );
            System.err.println( "--skip-compile is deprecated, please use --compile NONE" );
        }
        if ( ( dev || dontUpdate ) && options.has( jenkinsVersion ) )
        {
            System.err.println( "Using --dev or --dont-update with --rev makes no sense, exiting." );
            System.exit( 1 );
        }
        if ( compile.isEmpty() && !pullRequests.isEmpty() )
        {
            compile = new ArrayList<>();
            if ( getPullRequest( Repository.BUKKIT ) != null || getPullRequest( Repository.CRAFTBUKKIT ) != null )
            {
                compile.add( Compile.CRAFTBUKKIT );
            }

            if ( getPullRequest( Repository.SPIGOT ) != null )
            {
                compile.add( Compile.SPIGOT );
            }
        }

        try
        {
            runProcess( CWD, "sh", "-c", "exit" );
        } catch ( Exception ex )
        {
            if ( IS_WINDOWS )
            {
                String gitVersion = "PortableGit-2.30.0-" + ( System.getProperty( "os.arch" ).endsWith( "64" ) ? "64" : "32" ) + "-bit";
                // https://github.com/git-for-windows/git/releases/tag/v2.30.0.windows.1
                String gitHash = System.getProperty( "os.arch" ).endsWith( "64" ) ? "6497e30fc6141e3c27af6cc3a081861043a7666dd54f395d47184e8eb75f5d61" : "b3768c64b6afa082043659c56acb4c3483df6b6e884fdc7e3c769f7e7e99a3a8";
                msysDir = new File( gitVersion, "PortableGit" );

                if ( !msysDir.isDirectory() )
                {
                    System.out.println( "*** Could not find PortableGit installation, downloading. ***" );

                    String gitName = gitVersion + ".7z.exe";
                    File gitInstall = new File( gitVersion, gitName );
                    gitInstall.deleteOnExit();
                    gitInstall.getParentFile().mkdirs();

                    if ( !gitInstall.exists() )
                    {
                        download( "https://github.com/git-for-windows/git/releases/download/v2.30.0.windows.1/" + gitName, gitInstall, HashFormat.SHA256, gitHash );
                    }

                    System.out.println( "Extracting downloaded git install" );
                    // yes to all, silent, don't run. Only -y seems to work
                    runProcess( gitInstall.getParentFile(), gitInstall.getAbsolutePath(), "-y", "-gm2", "-nr" );

                    gitInstall.delete();
                }

                System.out.println( "*** Using downloaded git " + msysDir + " ***" );
                System.out.println( "*** Please note that this is a beta feature, so if it does not work please also try a manual install of git from https://git-for-windows.github.io/ ***" );
            } else
            {
                System.out.println( "You must run this jar through bash (msysgit)" );
                System.exit( 1 );
            }
        }

        try
        {
            runProcess( CWD, "git", "--version" );
        } catch ( Exception ex )
        {
            System.out.println( "Could not successfully run git. Please ensure it is installed and functioning. " + ex.getMessage() );
            System.exit( 1 );
        }

        try
        {
            runProcess( CWD, "git", "config", "--global", "--includes", "user.name" );
        } catch ( Exception ex )
        {
            System.out.println( "Git name not set, setting it to default value." );
            runProcess( CWD, "git", "config", "--global", "user.name", "BuildTools" );
        }
        try
        {
            runProcess( CWD, "git", "config", "--global", "--includes", "user.email" );
        } catch ( Exception ex )
        {
            System.out.println( "Git email not set, setting it to default value." );
            runProcess( CWD, "git", "config", "--global", "user.email", "unconfigured@null.spigotmc.org" );
        }

        try
        {
            runProcess( CWD, "java", "-version" );
        } catch ( Exception ex )
        {
            System.out.println( "Could not successfully run Java." + ex.getMessage() );
            System.exit( 1 );
        }

        if ( !dontUpdate && !dev )
        {
            String askedVersion = options.valueOf( jenkinsVersion );
            System.out.println( "Attempting to build version: '" + askedVersion + "' use --rev <version> to override" );

            String verInfo;
            try
            {
                verInfo = get( "https://hub.spigotmc.org/versions/" + askedVersion + ".json" );
            } catch ( IOException ex )
            {
                System.err.println( "Could not get version " + askedVersion + " does it exist? Try another version or use 'latest'" );
                ex.printStackTrace();
                System.exit( 1 );
                return;
            }
            System.out.println( "Found version" );
            System.out.println( verInfo );

            buildInfo = new Gson().fromJson( verInfo, BuildInfo.class );

            if ( buildNumber != -1 && buildInfo.getToolsVersion() != -1 && buildNumber < buildInfo.getToolsVersion() )
            {
                System.err.println( "**** Your BuildTools is out of date and will not build the requested version. Please grab a new copy from https://www.spigotmc.org/go/buildtools-dl" );
                System.exit( 1 );
            }

            if ( !options.has( disableJavaCheck ) )
            {
                if ( buildInfo.getJavaVersions() == null )
                {
                    buildInfo.setJavaVersions( new int[]
                    {
                        JavaVersion.JAVA_7.getVersion(), JavaVersion.JAVA_8.getVersion()
                    } );
                }

                Preconditions.checkArgument( buildInfo.getJavaVersions().length == 2, "Expected only two Java versions, got %s", JavaVersion.printVersions( buildInfo.getJavaVersions() ) );

                JavaVersion curVersion = JavaVersion.getCurrentVersion();
                JavaVersion minVersion = JavaVersion.getByVersion( buildInfo.getJavaVersions()[0] );
                JavaVersion maxVersion = JavaVersion.getByVersion( buildInfo.getJavaVersions()[1] );

                if ( curVersion.getVersion() < minVersion.getVersion() || curVersion.getVersion() > maxVersion.getVersion() )
                {
                    System.err.println( "*** The version you have requested to build requires Java versions between " + JavaVersion.printVersions( buildInfo.getJavaVersions() ) + ", but you are using " + curVersion );
                    System.err.println( "*** Please rerun BuildTools using an appropriate Java version. For obvious reasons outdated MC versions do not support Java versions that did not exist at their release." );
                    System.exit( 1 );
                }
            }
        }

        File workDir = new File( "work" );
        workDir.mkdir();

        File bukkit = new File( "Bukkit" );
        if ( !bukkit.exists() || !containsGit( bukkit ) )
        {
            clone( "https://hub.spigotmc.org/stash/scm/spigot/bukkit.git", bukkit );
        }

        File craftBukkit = new File( "CraftBukkit" );
        if ( !craftBukkit.exists() || !containsGit( craftBukkit ) )
        {
            clone( "https://hub.spigotmc.org/stash/scm/spigot/craftbukkit.git", craftBukkit );
        }

        File spigot = new File( "Spigot" );
        if ( !spigot.exists() || !containsGit( spigot ) )
        {
            clone( "https://hub.spigotmc.org/stash/scm/spigot/spigot.git", spigot );
        }

        File buildData = new File( "BuildData" );
        if ( !buildData.exists() || !containsGit( buildData ) )
        {
            clone( "https://hub.spigotmc.org/stash/scm/spigot/builddata.git", buildData );
        }

        String m2Home = System.getenv( "M2_HOME" );
        if ( m2Home == null || !( maven = new File( m2Home ) ).exists() )
        {
            String mavenVersion = "apache-maven-3.6.0";
            maven = new File( mavenVersion );

            if ( !maven.exists() )
            {
                System.out.println( "Maven does not exist, downloading. Please wait." );

                File mvnTemp = new File( mavenVersion + "-bin.zip" );
                mvnTemp.deleteOnExit();

                // https://www.apache.org/dist/maven/maven-3/3.6.0/binaries/apache-maven-3.6.0-bin.zip.sha512
                download( "https://static.spigotmc.org/maven/" + mvnTemp.getName(), mvnTemp, HashFormat.SHA512, "7d14ab2b713880538974aa361b987231473fbbed20e83586d542c691ace1139026f232bd46fdcce5e8887f528ab1c3fbfc1b2adec90518b6941235952d3868e9" );
                unzip( mvnTemp, new File( "." ) );
                mvnTemp.delete();
            }
        }

        Git bukkitGit = Git.open( bukkit );
        Git craftBukkitGit = Git.open( craftBukkit );
        Git spigotGit = Git.open( spigot );
        Git buildGit = Git.open( buildData );

        if ( !dontUpdate )
        {
            boolean buildDataChanged = pull( buildGit, buildInfo.getRefs().getBuildData(), null );
            boolean bukkitChanged = pull( bukkitGit, buildInfo.getRefs().getBukkit(), getPullRequest( Repository.BUKKIT ) );
            boolean craftBukkitChanged = pull( craftBukkitGit, buildInfo.getRefs().getCraftBukkit(), getPullRequest( Repository.CRAFTBUKKIT ) );
            boolean spigotChanged = pull( spigotGit, buildInfo.getRefs().getSpigot(), getPullRequest( Repository.SPIGOT ) );

            // Checks if any of the 4 repositories have been updated via a fetch, the --compile-if-changed flag is set and none of the repositories were cloned in this run.
            if ( !buildDataChanged && !bukkitChanged && !craftBukkitChanged && !spigotChanged && options.has( compileIfChanged ) && !didClone )
            {
                System.out.println( "*** No changes detected in any of the repositories!" );
                System.out.println( "*** Exiting due to the --compile-if-changes" );
                System.exit( 0 );
            }
        }

        VersionInfo versionInfo = new Gson().fromJson(
                Files.asCharSource( new File( "BuildData/info.json" ), Charsets.UTF_8 ).read(),
                VersionInfo.class
        );
        // Default to 1.8 builds.
        if ( versionInfo == null )
        {
            versionInfo = new VersionInfo( "1.8", "bukkit-1.8.at", "bukkit-1.8-cl.csrg", "bukkit-1.8-members.csrg", "package.srg", null );
        }
        System.out.println( "Attempting to build Minecraft with details: " + versionInfo );

        if ( buildNumber != -1 && versionInfo.getToolsVersion() != -1 && buildNumber < versionInfo.getToolsVersion() )
        {
            System.err.println( "" );
            System.err.println( "**** Your BuildTools is out of date and will not build the requested version. Please grab a new copy from https://www.spigotmc.org/go/buildtools-dl" );
            System.exit( 1 );
        }

        File vanillaJar = new File( workDir, "minecraft_server." + versionInfo.getMinecraftVersion() + ".jar" );
        File embeddedVanillaJar = new File( workDir, "server-" + versionInfo.getMinecraftVersion() + ".jar" );
        if ( !checkHash( vanillaJar, versionInfo ) )
        {
            if ( versionInfo.getServerUrl() != null )
            {
                download( versionInfo.getServerUrl(), vanillaJar, HashFormat.MD5, versionInfo.getMinecraftHash() );
            } else
            {
                download( getServerVanillaUrl( versionInfo.getMinecraftVersion() ), vanillaJar, HashFormat.MD5, versionInfo.getMinecraftHash() );
            }
        }

        try ( JarFile jar = new JarFile( vanillaJar ) )
        {
            ZipEntry entry = jar.getEntry( "META-INF/versions/" + versionInfo.getMinecraftVersion() + "/server-" + versionInfo.getMinecraftVersion() + ".jar" );
            if ( entry != null )
            {
                if ( !checkHash( embeddedVanillaJar, HashFormat.SHA256, versionInfo.getMinecraftHash() ) )
                {
                    try ( InputStream is = jar.getInputStream( entry ) )
                    {
                        byte[] embedded = ByteStreams.toByteArray( is );
                        if ( embedded != null )
                        {
                            Files.write( embedded, embeddedVanillaJar );
                        }
                    }

                    try ( FileSystem zipfs = FileSystems.newFileSystem( embeddedVanillaJar.toPath(), (ClassLoader) null ) )
                    {
                        java.nio.file.Files.delete( zipfs.getPath( "/META-INF/MOJANGCS.RSA" ) );
                        java.nio.file.Files.delete( zipfs.getPath( "/META-INF/MOJANGCS.SF" ) );
                    }
                }

                vanillaJar = embeddedVanillaJar;
            }
        }

        if ( versionInfo.getServerUrl() == null )
        {
            // Legacy versions can also specify a specific shell to build with which has to be bash-compatible
            applyPatchesShell = System.getenv().get( "SHELL" );
            if ( applyPatchesShell == null || applyPatchesShell.trim().isEmpty() )
            {
                applyPatchesShell = "bash";
            }
        }

        Iterable<RevCommit> mappings = buildGit.log()
                .addPath( "mappings/" )
                .setMaxCount( 1 ).call();

        Hasher mappingsHash = HashFormat.MD5.getHash().newHasher();
        for ( RevCommit rev : mappings )
        {
            mappingsHash.putString( rev.getName(), Charsets.UTF_8 );
        }
        String mappingsVersion = mappingsHash.hash().toString().substring( 24 ); // Last 8 chars

        File finalMappedJar = new File( workDir, "mapped." + mappingsVersion + ".jar" );
        if ( !finalMappedJar.exists() )
        {
            System.out.println( "Final mapped jar: " + finalMappedJar + " does not exist, creating (please wait)!" );

            File classMappings = new File( "BuildData/mappings/" + versionInfo.getClassMappings() );
            File memberMappings = new File( "BuildData/mappings/" + versionInfo.getMemberMappings() );
            File fieldMappings = new File( workDir, "bukkit-" + mappingsVersion + "-fields.csrg" );
            if ( versionInfo.getMappingsUrl() != null )
            {
                File mojangMappings = new File( workDir, "minecraft_server." + versionInfo.getMinecraftVersion() + ".txt" );
                if ( !mojangMappings.exists() )
                {
                    download( versionInfo.getMappingsUrl(), mojangMappings );
                }

                MapUtil mapUtil = new MapUtil();
                mapUtil.loadBuk( classMappings );
                if ( !memberMappings.exists() )
                {
                    memberMappings = new File( workDir, "bukkit-" + mappingsVersion + "-members.csrg" );
                    mapUtil.makeFieldMaps( mojangMappings, memberMappings, true );
                } else if ( !fieldMappings.exists() )
                {
                    mapUtil.makeFieldMaps( mojangMappings, fieldMappings, false );
                }

                // 1.17+
                if ( memberMappings.exists() )
                {
                    runMavenInstall( CWD, "install:install-file", "-Dfile=" + memberMappings, "-Dpackaging=csrg", "-DgroupId=org.spigotmc",
                            "-DartifactId=minecraft-server", "-Dversion=" + versionInfo.getSpigotVersion(), "-Dclassifier=maps-spigot-members", "-DgeneratePom=false" );
                }

                // 1.17
                if ( fieldMappings.exists() )
                {
                    runMavenInstall( CWD, "install:install-file", "-Dfile=" + fieldMappings, "-Dpackaging=csrg", "-DgroupId=org.spigotmc",
                            "-DartifactId=minecraft-server", "-Dversion=" + versionInfo.getSpigotVersion(), "-Dclassifier=maps-spigot-fields", "-DgeneratePom=false" );

                    File combinedMappings = new File( workDir, "bukkit-" + mappingsVersion + "-combined.csrg" );
                    if ( !combinedMappings.exists() )
                    {
                        mapUtil.makeCombinedMaps( combinedMappings, memberMappings );
                    }

                    runMavenInstall( CWD, "install:install-file", "-Dfile=" + combinedMappings, "-Dpackaging=csrg", "-DgroupId=org.spigotmc",
                            "-DartifactId=minecraft-server", "-Dversion=" + versionInfo.getSpigotVersion(), "-Dclassifier=maps-spigot", "-DgeneratePom=false" );
                } else
                {
                    // 1.18+
                    runMavenInstall( CWD, "install:install-file", "-Dfile=" + classMappings, "-Dpackaging=csrg", "-DgroupId=org.spigotmc",
                            "-DartifactId=minecraft-server", "-Dversion=" + versionInfo.getSpigotVersion(), "-Dclassifier=maps-spigot", "-DgeneratePom=false" );
                }

                // 1.17+
                runMavenInstall( CWD, "install:install-file", "-Dfile=" + mojangMappings, "-Dpackaging=txt", "-DgroupId=org.spigotmc",
                        "-DartifactId=minecraft-server", "-Dversion=" + versionInfo.getSpigotVersion(), "-Dclassifier=maps-mojang", "-DgeneratePom=false" );
            }

            File clMappedJar = new File( finalMappedJar + "-cl" );
            File mMappedJar = new File( finalMappedJar + "-m" );

            if ( versionInfo.getClassMapCommand() == null )
            {
                versionInfo.setClassMapCommand( "java -jar BuildData/bin/SpecialSource-2.jar map -i {0} -m {1} -o {2}" );
            }
            runProcess( CWD, MessageFormat.format( versionInfo.getClassMapCommand(), vanillaJar.getPath(), classMappings.getPath(), clMappedJar.getPath() ).split( " " ) );

            if ( versionInfo.getMemberMapCommand() == null )
            {
                versionInfo.setMemberMapCommand( "java -jar BuildData/bin/SpecialSource-2.jar map -i {0} -m {1} -o {2}" );
            }
            runProcess( CWD, MessageFormat.format( versionInfo.getMemberMapCommand(), clMappedJar.getPath(),
                    memberMappings.getPath(), mMappedJar.getPath() ).split( " " ) );

            if ( versionInfo.getFinalMapCommand() == null )
            {
                versionInfo.setFinalMapCommand( "java -jar BuildData/bin/SpecialSource.jar --kill-lvt -i {0} --access-transformer {1} -m {2} -o {3}" );
            }
            runProcess( CWD, MessageFormat.format( versionInfo.getFinalMapCommand(), mMappedJar.getPath(), "BuildData/mappings/" + versionInfo.getAccessTransforms(),
                    ( versionInfo.getPackageMappings() == null ) ? fieldMappings.getPath() : "BuildData/mappings/" + versionInfo.getPackageMappings(), finalMappedJar.getPath() ).split( " " ) );
        }

        runMavenInstall( CWD, "install:install-file", "-Dfile=" + finalMappedJar, "-Dpackaging=jar", "-DgroupId=org.spigotmc",
                "-DartifactId=minecraft-server", "-Dversion=" + ( versionInfo.getSpigotVersion() != null ? versionInfo.getSpigotVersion() : versionInfo.getMinecraftVersion() + "-SNAPSHOT" ) );

        File decompileDir = new File( workDir, "decompile-" + mappingsVersion );
        if ( !decompileDir.exists() )
        {
            decompileDir.mkdir();

            File clazzDir = new File( decompileDir, "classes" );
            unzip( finalMappedJar, clazzDir, (input) -> input.startsWith( "net/minecraft" ) );
            if ( versionInfo.getDecompileCommand() == null )
            {
                versionInfo.setDecompileCommand( "java -jar BuildData/bin/fernflower.jar -dgs=1 -hdc=0 -rbr=0 -asc=1 -udv=0 {0} {1}" );
            }

            runProcess( CWD, MessageFormat.format( versionInfo.getDecompileCommand(), clazzDir.getPath(), decompileDir.getPath() ).split( " " ) );
        }

        try
        {
            File latestLink = new File( workDir, "decompile-latest" );
            latestLink.delete();

            java.nio.file.Files.createSymbolicLink( latestLink.toPath(), decompileDir.getParentFile().toPath().relativize( decompileDir.toPath() ) );
        } catch ( UnsupportedOperationException ex )
        {
            // Ignore if not possible
        } catch ( FileSystemException ex )
        {
            // Not running as admin on Windows
        } catch ( IOException ex )
        {
            System.out.println( "Did not create decompile-latest link " + ex.getMessage() );
        }

        System.out.println( "Applying CraftBukkit Patches" );
        File nmsDir = new File( craftBukkit, "src/main/java/net" );
        if ( nmsDir.exists() )
        {
            System.out.println( "Backing up NMS dir" );
            FileUtils.moveDirectory( nmsDir, new File( workDir, "nms.old." + System.currentTimeMillis() ) );
        }
        Path patchDir = new File( craftBukkit, "nms-patches" ).toPath();
        java.nio.file.Files.walk( patchDir ).filter( java.nio.file.Files::isRegularFile ).forEach( (path) ->
        {
            File file = path.toFile();
            if ( !file.getName().endsWith( ".patch" ) )
            {
                return;
            }

            String relativeName = patchDir.relativize( path ).toString().replace( ".patch", ".java" );
            String targetFile = ( relativeName.contains( File.separator ) ) ? relativeName : "net/minecraft/server/" + relativeName;

            File clean = new File( decompileDir, targetFile );
            File t = new File( nmsDir.getParentFile(), targetFile );
            t.getParentFile().mkdirs();

            System.out.println( "Patching " + relativeName );

            try
            {
                List<String> readFile = Files.readLines( file, Charsets.UTF_8 );

                // Manually append prelude if it is not found in the first few lines.
                boolean preludeFound = false;
                for ( int i = 0; i < Math.min( 3, readFile.size() ); i++ )
                {
                    if ( readFile.get( i ).startsWith( "+++" ) )
                    {
                        preludeFound = true;
                        break;
                    }
                }
                if ( !preludeFound )
                {
                    readFile.add( 0, "+++" );
                }

                Patch parsedPatch = DiffUtils.parseUnifiedDiff( readFile );
                List<?> modifiedLines = DiffUtils.patch( Files.readLines( clean, Charsets.UTF_8 ), parsedPatch );

                try ( BufferedWriter bw = new BufferedWriter( new FileWriter( t ) ) )
                {
                    for ( Object line : modifiedLines )
                    {
                        bw.write( (String) line );
                        bw.newLine();
                    }
                }
            } catch ( Exception ex )
            {
                throw new RuntimeException( "Error patching " + relativeName, ex );
            }
        } );
        File tmpNms = new File( craftBukkit, "tmp-nms" );
        FileUtils.copyDirectory( nmsDir, tmpNms );

        craftBukkitGit.branchDelete().setBranchNames( "patched" ).setForce( true ).call();
        craftBukkitGit.checkout().setCreateBranch( true ).setForceRefUpdate( true ).setName( "patched" ).call();
        craftBukkitGit.add().addFilepattern( "src/main/java/net/" ).call();
        craftBukkitGit.commit().setGpgConfig( new GpgConfig( null, null, null ) ).setSign( false ).setMessage( "CraftBukkit $ " + new Date() ).call();
        PullRequest craftBukkitPullRequest = getPullRequest( Repository.CRAFTBUKKIT );
        craftBukkitGit.checkout().setName( ( craftBukkitPullRequest == null ) ? buildInfo.getRefs().getCraftBukkit() : "origin/pr/" + craftBukkitPullRequest.getId() ).call();

        FileUtils.moveDirectory( tmpNms, nmsDir );

        if ( versionInfo.getToolsVersion() < 93 )
        {
            File spigotApi = new File( spigot, "Bukkit" );
            if ( !spigotApi.exists() )
            {
                clone( "file://" + bukkit.getAbsolutePath(), spigotApi );
            }
            File spigotServer = new File( spigot, "CraftBukkit" );
            if ( !spigotServer.exists() )
            {
                clone( "file://" + craftBukkit.getAbsolutePath(), spigotServer );
            }
        }

        // Git spigotApiGit = Git.open( spigotApi );
        // Git spigotServerGit = Git.open( spigotServer );
        if ( compile == null || compile.isEmpty() )
        {
            if ( versionInfo.getToolsVersion() <= 104 || dev )
            {
                compile = Arrays.asList( Compile.CRAFTBUKKIT, Compile.SPIGOT );
            } else
            {
                compile = Collections.singletonList( Compile.SPIGOT );
            }
        }
        if ( compile.contains( Compile.CRAFTBUKKIT ) )
        {
            System.out.println( "Compiling Bukkit" );
            runMavenAPI( bukkit, "clean", "install" );
            if ( generateDocs )
            {
                runMavenAPI( bukkit, "javadoc:jar" );
            }
            if ( generateSource )
            {
                runMavenAPI( bukkit, "source:jar" );
            }

            System.out.println( "Compiling CraftBukkit" );
            runMavenServer( craftBukkit, "clean", "install" );
        }

        try
        {
            if ( compile.contains( Compile.SPIGOT ) )
            {
                runProcess( spigot, applyPatchesShell, "applyPatches.sh" );
                System.out.println( "*** Spigot patches applied!" );

                System.out.println( "Compiling Spigot & Spigot-API" );
                runMavenServer( spigot, "clean", "install" );

                File spigotApi = new File( spigot, "Spigot-API" );
                if ( generateDocs )
                {
                    runMavenAPI( spigotApi, "javadoc:jar" );
                }
                if ( generateSource )
                {
                    runMavenAPI( spigotApi, "source:jar" );
                }
            }
        } catch ( Exception ex )
        {
            System.err.println( "Error compiling Spigot. Please check the wiki for FAQs." );
            System.err.println( "If this does not resolve your issue then please pastebin the entire BuildTools.log.txt file when seeking support." );
            ex.printStackTrace();
            System.exit( 1 );
        }

        for ( int i = 0; i < 35; i++ )
        {
            System.out.println( " " );
        }

        System.out.println( "Success! Everything completed successfully. Copying final .jar files now." );

        String base = ( versionInfo.getSpigotVersion() != null ) ? "-" + versionInfo.getSpigotVersion() : "";
        String bootstrap = ( versionInfo.getToolsVersion() >= 138 ) ? "-bootstrap" : "";
        String suffix = base + bootstrap + ".jar";
        if ( compile.contains( Compile.CRAFTBUKKIT ) && ( versionInfo.getToolsVersion() < 101 || versionInfo.getToolsVersion() > 104 ) )
        {
            copyJar( "CraftBukkit/target", "craftbukkit", suffix, new File( outputDir.value( options ), "craftbukkit-" + versionInfo.getMinecraftVersion() + ".jar" ) );
        }
        if ( compile.contains( Compile.SPIGOT ) )
        {
            copyJar( "Spigot/Spigot-Server/target", "spigot", suffix, new File( outputDir.value( options ), "spigot-" + versionInfo.getMinecraftVersion() + ".jar" ) );
        }
    }

    private static boolean checkHash(File vanillaJar, VersionInfo versionInfo) throws IOException
    {
        if ( versionInfo.getShaServerHash() != null )
        {
            return checkHash( vanillaJar, HashFormat.SHA1, versionInfo.getShaServerHash() );
        } else if ( versionInfo.getMinecraftHash() != null )
        {
            return checkHash( vanillaJar, HashFormat.MD5, versionInfo.getMinecraftHash() );
        } else
        {
            return vanillaJar.isFile();
        }
    }

    private static boolean checkHash(File vanillaJar, HashFormat hashFormat, String goodHash) throws IOException
    {
        if ( !vanillaJar.isFile() )
        {
            return false;
        }

        if ( dev )
        {
            return true;
        }

        String hash = Files.asByteSource( vanillaJar ).hash( hashFormat.getHash() ).toString();
        boolean result = hash.equals( goodHash );

        if ( !result )
        {
            System.err.println( "**** Warning, Minecraft jar hash of " + hash + " does not match stored hash of " + goodHash );
            return false;
        } else
        {
            System.out.println( "Found good Minecraft hash (" + hash + ")" );
            return true;
        }
    }

    public static final String get(String url) throws IOException
    {
        URLConnection con = new URL( url ).openConnection();
        con.setConnectTimeout( 30000 );
        con.setReadTimeout( 30000 );

        try ( InputStreamReader r = new InputStreamReader( con.getInputStream() ) )
        {
            return CharStreams.toString( r );
        }
    }

    public static void copyJar(String path, final String jarPrefix, final String jarSuffix, File outJar) throws Exception
    {
        File[] files = new File( path ).listFiles( (dir, name) -> name.startsWith( jarPrefix ) && name.endsWith( jarSuffix ) );

        if ( !outJar.getParentFile().isDirectory() )
        {
            outJar.getParentFile().mkdirs();
        }

        for ( File file : files )
        {
            System.out.println( "Copying " + file.getName() + " to " + outJar.getAbsolutePath() );
            Files.copy( file, outJar );
            System.out.println( "  - Saved as " + outJar );
        }
    }

    public static boolean pull(Git repo, String ref, PullRequest pullRequest) throws Exception
    {
        System.out.println( "Pulling updates for " + repo.getRepository().getDirectory() );

        try
        {
            repo.reset().setRef( "origin/master" ).setMode( ResetCommand.ResetType.HARD ).call();
        } catch ( JGitInternalException ex )
        {
            System.err.println( "*** Warning, could not find origin/master ref, but continuing anyway." );
            System.err.println( "*** If further errors occur please delete " + repo.getRepository().getDirectory().getParent() + " and retry." );
        }

        FetchResult result;
        if ( pullRequest != null )
        {
            result = repo.fetch().setRefSpecs( new RefSpec( "+refs/pull-requests/" + pullRequest.getId() + "/from:refs/remotes/origin/pr/" + pullRequest.getId() ) ).call();
        } else
        {
            result = repo.fetch().call();
        }

        System.out.println( "Successfully fetched updates!" );

        if ( pullRequest != null )
        {
            repo.checkout().setName( "origin/pr/" + pullRequest.getId() ).setForced( true ).call();
        } else
        {
            repo.reset().setRef( ref ).setMode( ResetCommand.ResetType.HARD ).call();
        }

        if ( ref.equals( "master" ) )
        {
            repo.reset().setRef( "origin/master" ).setMode( ResetCommand.ResetType.HARD ).call();
        }
        System.out.println( "Checked out: " + ref );

        // Return true if fetch changed any tracking refs.
        return !result.getTrackingRefUpdates().isEmpty();
    }

    public static PullRequest getPullRequest(Repository repository)
    {
        for ( PullRequest request : pullRequests )
        {
            if ( request.getRepository() == repository )
            {
                return request;
            }
        }

        return null;
    }

    public static void validatedPullRequestsOptions()
    {
        Set<Repository> repositories = EnumSet.noneOf( Repository.class );

        for ( PullRequest pullRequest : pullRequests )
        {
            if ( !repositories.add( pullRequest.getRepository() ) )
            {
                throw new RuntimeException( "Pull request option for repository " + pullRequest.getRepository() + " is present multiple times. Only one per repository can be specified." );
            }
        }
    }

    private static int runMavenInstall(File workDir, String... command) throws Exception
    {
        return runMaven0( workDir, false, false, command );
    }

    private static int runMavenAPI(File workDir, String... command) throws Exception
    {
        return runMaven0( workDir, dev, false, command );
    }

    private static int runMavenServer(File workDir, String... command) throws Exception
    {
        return runMaven0( workDir, dev, remapped, command );
    }

    private static int runMaven0(File workDir, boolean dev, boolean remapped, String... command) throws Exception
    {
        List<String> args = new LinkedList<>();

        if ( IS_WINDOWS )
        {
            args.add( maven.getAbsolutePath() + "/bin/mvn.cmd" );
        } else
        {
            args.add( "sh" );
            args.add( maven.getAbsolutePath() + "/bin/mvn" );
        }

        args.add( "-Dbt.name=" + buildInfo.getName() );

        if ( dev )
        {
            args.add( "-P" );
            args.add( "development" );
        }
        if ( remapped )
        {
            args.add( "-P" );
            args.add( "remapped" );
        }

        args.addAll( Arrays.asList( command ) );

        return runProcess( workDir, args.toArray( new String[ args.size() ] ) );
    }

    public static int runProcess(File workDir, String... command) throws Exception
    {
        if ( command[0].equals( "java" ) )
        {
            command[0] = System.getProperty( "java.home" ) + "/bin/" + command[0];
        }

        if ( msysDir != null )
        {
            if ( "bash".equals( command[0] ) )
            {
                command[0] = "git-bash";
            }

            // BUILDTOOLS-594, etc: Many broken systems lack cmd.exe in PATH for unknown reasons (user error?)
            String cmd = System.getenv( "ComSpec" );
            if ( cmd == null )
            {
                // Hopefully nothing messes up both PATH and ComSpec (what a broken system)
                cmd = "cmd.exe";
            }

            String[] shim = new String[]
            {
                cmd, "/D", "/C"
            };
            command = ObjectArrays.concat( shim, command, String.class );
        }
        return runProcess0( workDir, command );
    }

    private static int runProcess0(File workDir, String... command) throws Exception
    {
        Preconditions.checkArgument( workDir != null, "workDir" );
        Preconditions.checkArgument( command != null && command.length > 0, "Invalid command" );

        ProcessBuilder pb = new ProcessBuilder( command );
        pb.directory( workDir );
        pb.environment().put( "JAVA_HOME", System.getProperty( "java.home" ) );
        pb.environment().remove( "M2_HOME" ); // Just let maven figure this out from where it is invoked
        if ( !pb.environment().containsKey( "MAVEN_OPTS" ) )
        {
            pb.environment().put( "MAVEN_OPTS", "-Xmx1024M" );
        }
        if ( !pb.environment().containsKey( "_JAVA_OPTIONS" ) )
        {
            String javaOptions = "-Djdk.net.URLClassPath.disableClassPathURLCheck=true";

            for ( String arg : ManagementFactory.getRuntimeMXBean().getInputArguments() )
            {
                if ( arg.startsWith( "-Xmx" ) )
                {
                    javaOptions += " " + arg;
                }
            }

            pb.environment().put( "_JAVA_OPTIONS", javaOptions );
        }
        if ( IS_WINDOWS )
        {
            String pathEnv = null;
            for ( String key : pb.environment().keySet() )
            {
                if ( key.equalsIgnoreCase( "path" ) )
                {
                    pathEnv = key;
                }
            }
            if ( pathEnv == null )
            {
                throw new IllegalStateException( "Could not find path variable!" );
            }

            if ( msysDir != null )
            {
                String path = msysDir.getAbsolutePath() + ";" + new File( msysDir, "bin" ).getAbsolutePath() + ";" + pb.environment().get( pathEnv );
                pb.environment().put( pathEnv, path );
            }

            String path = pb.environment().get( pathEnv );
            // Not strictly correct, but least likely to be a false positive
            if ( !path.contains( "C:\\WINDOWS\\system32;" ) )
            {
                path = System.getenv( "SystemRoot" ) + "\\system32;" + path;
                pb.environment().put( pathEnv, path );
            }
        }

        final Process ps = pb.start();

        new Thread( new StreamRedirector( ps.getInputStream(), System.out ), "System.out redirector" ).start();
        new Thread( new StreamRedirector( ps.getErrorStream(), System.err ), "System.err redirector" ).start();

        int status = ps.waitFor();

        if ( status != 0 )
        {
            throw new RuntimeException( "Error running command, return status !=0: " + Arrays.toString( command ) );
        }

        return status;
    }

    @RequiredArgsConstructor
    private static class StreamRedirector implements Runnable
    {

        private final InputStream in;
        private final PrintStream out;

        @Override
        public void run()
        {
            try ( BufferedReader br = new BufferedReader( new InputStreamReader( in ) ) )
            {
                String line;
                while ( ( line = br.readLine() ) != null )
                {
                    out.println( line );
                }
            } catch ( IOException ex )
            {
                throw new RuntimeException( ex );
            }
        }
    }

    public static void unzip(File zipFile, File targetFolder) throws IOException
    {
        unzip( zipFile, targetFolder, null );
    }

    public static void unzip(File zipFile, File targetFolder, Predicate<String> filter) throws IOException
    {
        targetFolder.mkdir();
        try ( ZipFile zip = new ZipFile( zipFile ) )
        {
            for ( Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); )
            {
                ZipEntry entry = entries.nextElement();

                if ( filter != null )
                {
                    if ( !filter.test( entry.getName() ) )
                    {
                        continue;
                    }
                }

                File outFile = new File( targetFolder, entry.getName() );

                if ( entry.isDirectory() )
                {
                    outFile.mkdirs();
                    continue;
                }
                if ( outFile.getParentFile() != null )
                {
                    outFile.getParentFile().mkdirs();
                }

                try ( InputStream is = zip.getInputStream( entry ); OutputStream os = new FileOutputStream( outFile ); )
                {
                    ByteStreams.copy( is, os );
                }

                System.out.println( "Extracted: " + outFile );
            }
        }
    }

    public static void clone(String url, File target) throws GitAPIException, IOException
    {
        System.out.println( "Starting clone of " + url + " to " + target );

        try ( Git result = Git.cloneRepository().setURI( url ).setDirectory( target ).call() )
        {
            StoredConfig config = result.getRepository().getConfig();
            config.setBoolean( "core", null, "autocrlf", autocrlf );
            config.save();

            didClone = true;
            System.out.println( "Cloned git repository " + url + " to " + target.getAbsolutePath() + ". Current HEAD: " + commitHash( result ) );
        }
    }

    public static String commitHash(Git repo) throws GitAPIException
    {
        return Iterables.getOnlyElement( repo.log().setMaxCount( 1 ).call() ).getName();
    }

    public static File download(String url, File target) throws IOException
    {
        return download( url, target, HashFormat.SHA1, "!" );
    }

    public static File download(String url, File target, HashFormat hashFormat, String goodHash) throws IOException
    {
        String shaHash = VersionInfo.hashFromUrl( url );
        if ( shaHash != null )
        {
            hashFormat = HashFormat.SHA1;
            goodHash = shaHash;
        }

        System.out.println( "Starting download of " + url );

        byte[] bytes;
        if (Builder.proxyAvailable)
            bytes = ProxyResources.toByteArray(new URL(url), Builder.proxyAddress, Builder.proxyPort);
        else
            bytes = Resources.toByteArray(new URL(url));
        String hash = hashFormat.getHash().hashBytes( bytes ).toString();

        System.out.println( "Downloaded file: " + target + " with hash: " + hash );

        if ( !dev && goodHash != null && !goodHash.equals( hash ) )
        {
            throw new IllegalStateException( "Downloaded file: " + target + " did not match expected hash: " + goodHash );
        }

        Files.write( bytes, target );

        return target;
    }

    public static String getServerVanillaUrl(String version) throws Exception
    {
        Gson gson = new Gson();

        String responseManifest = get( "https://launchermeta.mojang.com/mc/game/version_manifest.json" );
        JsonObject manifest = gson.fromJson( responseManifest, JsonObject.class );

        JsonArray manifestVersions = manifest.getAsJsonArray( "versions" );
        for ( JsonElement manifestVersionElement : manifestVersions )
        {
            if ( manifestVersionElement.isJsonObject() )
            {
                JsonObject manifestVersion = manifestVersionElement.getAsJsonObject();
                if ( manifestVersion.get( "id" ).getAsString().equals( version ) )
                {
                    String urlVersionData = manifestVersion.get( "url" ).getAsString();

                    String responseVersionData = get( urlVersionData );
                    JsonObject versionData = gson.fromJson( responseVersionData, JsonObject.class );
                    return versionData.getAsJsonObject( "downloads" ).getAsJsonObject( "server" ).get( "url" ).getAsString();
                }
            }
        }

        throw new RuntimeException( "Error cannot get the URL for legacy server version " + version );
    }

    public static void disableHttpsCertificateCheck()
    {
        // This globally disables certificate checking
        // http://stackoverflow.com/questions/19723415/java-overriding-function-to-disable-ssl-certificate-check
        try
        {
            TrustManager[] trustAllCerts = new TrustManager[]
            {
                new X509TrustManager()
                {
                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers()
                    {
                        return null;
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType)
                    {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType)
                    {
                    }
                }
            };

            // Trust SSL certs
            SSLContext sc = SSLContext.getInstance( "SSL" );
            sc.init( null, trustAllCerts, new SecureRandom() );
            HttpsURLConnection.setDefaultSSLSocketFactory( sc.getSocketFactory() );

            // Trust host names
            HttpsURLConnection.setDefaultHostnameVerifier( (hostname, session) -> true );
        } catch ( NoSuchAlgorithmException | KeyManagementException ex )
        {
            System.out.println( "Failed to disable https certificate check" );
            ex.printStackTrace( System.err );
        }
    }

    public static void logOutput()
    {
        try
        {
            final OutputStream logOut = new BufferedOutputStream( new FileOutputStream( LOG_FILE ) );

            Runtime.getRuntime().addShutdownHook( new Thread()
            {
                @Override
                public void run()
                {
                    System.setOut( new PrintStream( new FileOutputStream( FileDescriptor.out ) ) );
                    System.setErr( new PrintStream( new FileOutputStream( FileDescriptor.err ) ) );
                    try
                    {
                        logOut.close();
                    } catch ( IOException ex )
                    {
                        // We're shutting the jvm down anyway.
                    }
                }
            } );

            System.setOut( new PrintStream( new TeeOutputStream( System.out, logOut ) ) );
            System.setErr( new PrintStream( new TeeOutputStream( System.err, logOut ) ) );
        } catch ( FileNotFoundException ex )
        {
            System.err.println( "Failed to create log file: " + LOG_FILE );
        }
    }

    public enum HashFormat
    {
        MD5
        {
            @Override
            @SuppressWarnings("deprecation")
            public HashFunction getHash()
            {
                return Hashing.md5();
            }
        }, SHA1
        {
            @Override
            @SuppressWarnings("deprecation")
            public HashFunction getHash()
            {
                return Hashing.sha1();
            }
        }, SHA256
        {
            @Override
            public HashFunction getHash()
            {
                return Hashing.sha256();
            }
        }, SHA512
        {
            @Override
            public HashFunction getHash()
            {
                return Hashing.sha512();
            }
        };

        public abstract HashFunction getHash();
    }

    private static boolean containsGit(File file)
    {
        return new File( file, ".git" ).isDirectory();
    }
}
