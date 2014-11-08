package org.spigotmc.patcher;

import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import java.awt.GraphicsEnvironment;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.jbeat.Patcher;

public class Main
{

    public static final File path = new File("C:\\Users\\" + System.getProperty("user.name") + "\\AppData\\Local\\SpigotPatcherTMP\\");
    static boolean downloadLatest;
    static boolean useInternal;

    public static void main(String[] args)
    {
        if ( !GraphicsEnvironment.isHeadless() && args.length == 0 )
        {
            UserInterface.main( args );
            return;
        }

        if ( args.length == 3 )
        {
            System.out.println( "Welcome to the Spigot patch applicator." );
            System.out.println( "In order to use this tool you will need to specify three command line arguments as follows:" );
            System.out.println( "\tjava -jar SpigotPatcher.jar original.jar patch.bps output.jar [-o] [-l txtfile] [-"
                    + "ml txtfile" );
            System.out.println( "This will apply the specified patch to the original jar and save it to the output jar" );
            System.out.println( "Please ensure that you save your original jar for later use." );
            System.out.println( "If you have any queries, please direct them to http://www.spigotmc.org/" );
            return;
        }
        sortArgs(args);
        preparePatch(args);
    }

    @SuppressWarnings("TooBroadCatch")
    public static void patchSafe(PrintWriter console, File originalFile, File patchFile, File outputFile)
    {
        try
        {
            patch( console, originalFile, patchFile, outputFile );
        } catch ( Exception ex )
        {
            console.println( "***** Unknown error occured during patch process:" );
            ex.printStackTrace( console );
        }
    }

    @SuppressWarnings("TooBroadCatch")
    public static void patch(PrintWriter console, File originalFile, File patchFile, File outputFile) throws IOException
    {
        if ( !originalFile.canRead() )
        {
            console.println( "Specified original file " + originalFile + " does not exist or cannot be read!" );
            return;
        }
        if ( !patchFile.canRead() )
        {
            console.println( "Specified patch file " + patchFile + " does not exist or cannot be read!!" );
            return;
        }
        if ( outputFile.exists() )
        {
            console.println( "Specified output file " + outputFile + " exists, please remove it before running this program!" );
            return;
        }
        if ( !outputFile.createNewFile() )
        {
            console.println( "Could not create specified output file " + outputFile + " please ensure that it is in a valid directory which can be written to." );
            return;
        }

        console.println( "***** Starting patching process, please wait." );
        console.println( "\tInput md5 Checksum: " + Files.hash( originalFile, Hashing.md5() ) );
        console.println( "\tPatch md5 Checksum: " + Files.hash( patchFile, Hashing.md5() ) );

        try
        {
            new Patcher( patchFile, originalFile, outputFile ).patch();
        } catch ( Exception ex )
        {
            console.println( "***** Exception occured whilst patching file!" );
            ex.printStackTrace( console );
            outputFile.delete();
            return;
        }

        console.println( "***** Your file has been patched and verified! We hope you enjoy using Spigot!" );
        console.println( "\tOutput md5 Checksum: " + Files.hash( outputFile, Hashing.md5() ) );
        
        if(useInternal)
            originalFile.delete();
    }
    
    public static void sortArgs(String[] args) {
        int i = 0;
        for(String arg : args) {
            if(arg.startsWith("-")){
                switch(arg.substring(1)) {
                    case "i":
                        useInternal = true;
                        break;
                    case "dl":
                        downloadLatest = true;
                        break;
                    default:
                        break;
                }
            }
            i++;
        }
    }
    
    public static void preparePatch(String[] args) {
        File originalJar = null;
        File patch = null;
        if(useInternal) {
            originalJar = makeOriginalJar();            
        }
        else {
            originalJar = new File(args[0]);
        }
        if(downloadLatest) {
            patch = new File(path.getAbsoluteFile() + "\\" + downloadPatch().getName());
        }
        else  {
            patch = new File(args[1]);
        }
        System.out.println(patch.getAbsoluteFile());
        patchSafe( new PrintWriter( System.out ), originalJar, patch, new File( args[2] ) );
    }
    
    private static File makeOriginalJar() {      
        path.mkdir();
        File baseFile = loadFile("/Base.File", path.getAbsoluteFile() + "\\" + "Base.File"); 
        File patch = loadFile("/Base-Patch.bps", path.getAbsoluteFile() + "\\" + "Base-Patch.bps");
        
        patchSafe(new PrintWriter(System.out), baseFile, patch, new File(path.getAbsoluteFile() + "\\" + "original.jar"));
        File originalJar = new File(path.getAbsoluteFile() + "\\" + "original.jar");
        
        
        return originalJar;
    }
    
    private static File loadFile(String loc, String out) {
        InputStream is = new Main().getClass().getResourceAsStream(loc);
        return new File(writeFile(getISBytes(is), out));
    }
    
        //Download Function, author: Pigeoncraft
    public static File downloadPatch() {
        java.util.List<String> allPatches = new ArrayList<String>();
        try {
            System.out.println("Checking spigot for the latest patch ...");
            String url = "http://www.spigotmc.org/spigot-updates/";
            // Fetch the index
            URL address = new URL(url);
            HttpURLConnection con = (HttpURLConnection) address.openConnection();
            con.setRequestMethod("GET");

            // Cloudfare doesn't like empty user agents
            con.setRequestProperty("User-Agent", "Mozilla/5.0");

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuffer response = new StringBuffer();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            String sourceStr = response.toString();
            Matcher m = Pattern.compile("spigot([-+]\\d+)([a-z])\\.bps").matcher(sourceStr);
            while (m.find()) {
                allPatches.add(m.group());
            }
        } catch (Exception ex) {
            // Error
            System.out.println("Something went wrong checking for the latest patch!");
            System.out.println("Please download it manually from:\nhttp://spigotmc.org/spigot-updates/");
        }
        if (allPatches.size() != 0) try {
            if(isLatest(allPatches.get(allPatches.size() - 1), getOfflineFile())) {
                System.out.println("No update found.");
                return new File(getOfflineFile());  
            }              
                
            System.out.println("Downloading latest file [" + allPatches.get(allPatches.size() - 1) + "] ...");
            URL address = new URL("http://www.spigotmc.org/spigot-updates/" + allPatches.get(allPatches.size() - 1));
            HttpURLConnection con = (HttpURLConnection) address.openConnection();
            con.setRequestMethod("GET");

            // Cloudfare doesn't like empty user agents
            con.setRequestProperty("User-Agent", "Mozilla/5.0");

            File file = new File(allPatches.get(allPatches.size() - 1));
            BufferedInputStream bis = new BufferedInputStream(con.getInputStream());
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(path.getAbsoluteFile() + "\\" + file.getName()));
            int i = 0;
            while ((i = bis.read()) != -1) {
                bos.write(i);
            }
            bos.flush();
            bis.close();
            bos.close();
            System.out.println("Download complete!");
            return file;
        } catch (Exception ex) {
            ex.printStackTrace();
            // Error
            System.out.println("Something went wrong downloading the latest patch!");
            System.out.println("Please download it manually from:http://spigotmc.org/spigot-updates/");
        }
        return null;
    }
    
    public static byte[] getISBytes(InputStream is) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[16384];

        try
        {
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
        } catch ( IOException ex )
        {
            Logger.getLogger( Main.class.getName() ).log( Level.SEVERE, null, ex );
        }

        try
        {
            buffer.flush();
        } catch ( IOException ex )
        {
            Logger.getLogger( Main.class.getName() ).log( Level.SEVERE, null, ex );
        }

        return buffer.toByteArray();        
    }
    
    public static String writeFile(byte[] b, String loc) {
        FileOutputStream fos = null;
        try
        {        
            fos = new FileOutputStream(loc);
            fos.write(b);
        } catch ( FileNotFoundException ex )
        {
            Logger.getLogger( Main.class.getName() ).log( Level.SEVERE, null, ex );
        } catch ( IOException ex )
        {
            Logger.getLogger( Main.class.getName() ).log( Level.SEVERE, null, ex );
        } finally
        {
            try
            {
                fos.close();
            } catch ( IOException ex )
            {
                Logger.getLogger( Main.class.getName() ).log( Level.SEVERE, null, ex );
            }
            return loc;
        }
    }
    
    static public boolean deleteDirectory(File path) {
        
        if( path.exists() ) {
            File[] files = path.listFiles();
            for(int i=0; i<files.length; i++) {
                if(files[i].isDirectory()) {
                    deleteDirectory(files[i]);
                }
                else {
                    files[i].delete();
                }
            }
        }
        return( path.delete() );
    }    
    
    static public boolean isLatest(String onlineLatest, String offlineLatest) {
        if(offlineLatest.equals("null"))
            return false;
        if(getPatchDate(onlineLatest) > getPatchDate(offlineLatest)) 
            return false;
        if(getPatchDate(onlineLatest) == getPatchDate(offlineLatest))
            if(getPatchRev(onlineLatest) > getPatchRev(offlineLatest))
                return false;
        return true;
    }
    
    static public int getPatchDate(String patch) {
        patch = patch.substring( 7, 15 );
        int i = Integer.parseInt( patch );
        return i;
    }
    
    static public char getPatchRev(String patch) {
        patch = patch.substring( 15, 16 );
        return patch.toCharArray()[0];
    }    
    
    static public String getOfflineFile() {
        int date = 0;
        char rev = 0;
        if( path.exists() ) {
            File[] files = path.listFiles();
            for(int i=0; i<files.length; i++) {
                if(Pattern.compile("spigot([-+]\\d+)([a-z])\\.bps").matcher(files[i].getName()).matches()) {
                    int tmpDate = getPatchDate(files[i].getName());
                    char tmpRev = getPatchRev(files[i].getName());
                    if(tmpDate > date) {
                        date = tmpDate;
                        rev = tmpRev;
                    }
                    if(tmpDate == date) {
                        if(tmpRev > rev) {
                            date = tmpDate;
                            rev = tmpRev;                           
                        }                          
                    }
                }
            }
        }       
        File f = new File(path.getAbsoluteFile() + "\\" + "spigot-" + date + rev + ".bps");
        if(f.exists())
            return "spigot-" + date + rev + ".bps";
        else
            return "null";
    }
}
