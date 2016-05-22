package featanalysis;

import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.detect.interest.InterestPointDetector;
import boofcv.alg.misc.ImageStatistics;
import boofcv.core.image.ConvertBufferedImage;
import boofcv.factory.feature.detect.interest.FactoryInterestPoint;
import boofcv.gui.feature.FancyInterestPointRender;
import boofcv.gui.image.ShowImages;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.BoofDefaults;
import boofcv.struct.image.ImageFloat32;
import boofcv.struct.image.ImageSingleBand;
import boofcv.struct.image.ImageUInt8;
import georegression.struct.point.Point2D_F64;
import nom.tam.fits.FitsException;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;

import edu.jhu.pha.sdss.fits.*;
import edu.jhu.pha.sdss.fits.FITSImage.DataTypeNotSupportedException;
import edu.jhu.pha.sdss.fits.FITSImage.NoImageDataFoundException;
import nom.tam.fits.*;
import java.io.*;

public class ImageFeatures  {// extends JFrame {

    //inner class Point
    public class Point{
        double x,y,radius;
        public double getX(){return x;}
        public double getR(){return radius;}
        public double getY(){return y;}
        public double distance(Point p){
            return Math.sqrt((x-p.x)*(x-p.x)+(y-p.y)*(y-p.y));
        }

        public Point(Point2D_F64 p, double r){
            x=p.x;
            y=p.y;
            radius = r;
        }
        public Point(){
            x=y=radius=-1;
        }

        public void draw(Graphics2D g2d, double xscale, double yscale){
            if (x<0||y<0||radius<0)
                return;
            BasicStroke bs = new BasicStroke((int) radius*2);
            g2d.setStroke(bs);
            g2d.drawOval((int)(x*xscale),(int)(y*yscale),(int) (2*radius),(int) (2*radius));
        }
        public JSONObject encode(){
            JSONObject o= new JSONObject();
            try {
                o.put("x", x);
                o.put("y", y);
                o.put("r", radius);
            }catch(Exception e){
                e.printStackTrace();
            }
            //System.out.println(o);
            return o;
        }

        public void decode(JSONObject o){
            try {
                x = o.getDouble("x");
                y = o.getDouble("y");
                radius = o.getDouble("r");
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        public String toString(){
            return "[x="+x+",y="+y+",r="+radius+"]";
        }
    }
    ///////////////////////////////////////////////////////////////////////////////////
    // inner class Line
    public class Line{
        Point start, end;
        public Line (Point s, Point e){
            start = s; end =e;
            // sort them in some order
        }
        public Line(){
            start = new Point();
            end = new Point();
        }

        public double getX(){ // vertical lines, s and e has the same x
            return start.getX();
        }
        public double thickness(){
            return (start.getR()+end.getR())/2;
        }
        public double length(){
            return start.distance(end);
        }
        public void draw(Graphics2D g2d, double xscale, double yscale){
            if (start.getX()<0||start.getY()<0) // empty line
                return;
            BasicStroke bs = new BasicStroke((int) Math.max(1, thickness()));
            g2d.setStroke(bs);
            g2d.drawLine((int)(start.x*xscale), (int)(start.y*yscale), (int)(end.x*xscale), (int) (end.y*yscale));
        }

        public double distance(Line ln){
            // x distance between the lines
            double min_d=10000;
            double d;
            d = start.distance(ln.start);
            if(min_d >d )
                min_d = d;
            d = start.distance(ln.end);
            if(min_d >d )
                min_d = d;
            d = end.distance(ln.start);
            if(min_d >d )
                min_d = d;
            d = end.distance(ln.end);
            if(min_d >d )
                min_d = d;
            return min_d;
        }

        public String toString(){
            return "["+start.toString()+","+end.toString()+"]";
        }

        public JSONObject encode(){
            JSONObject o = new JSONObject();
            try{
                o.put("start", start.encode());
                o.put("end", end.encode());
            }catch(Exception e){
                e.printStackTrace();
            }
            return o;
        }
        public void decode(JSONObject o){
            try{
                JSONObject v = (JSONObject) o.get("start");
                start = new Point();
                start.decode(v);
                v = (JSONObject) o.get("end");
                end = new Point();
                end.decode(v);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
    //////////////////////////////////////////////////////////////////////////////////////////

    Vector<Point> points0; // points0 all detected points. points are the results after line detection
    String file;
    Vector<Line> lines;
    Vector<Point> points;
    double havg, hmax, hstd;
    public ImageFeatures() {
        points0 = new Vector<Point>();
        points = new Vector<Point>();
        lines = new Vector<Line>();
        // for testing visualization
        /*
        Toolkit tk=getToolkit();
        Dimension screenSize = tk.getScreenSize();
        setTitle("Image Quality Labeling");
        setPreferredSize(screenSize);
        */
    }

    public void generate(InputStream Stream) throws FitsException, DataTypeNotSupportedException, NoImageDataFoundException, IOException {
        
        Fits fit=new Fits(Stream);
        FITSImage fits = new FITSImage(fit);
        BufferedImage image = fits.getSubimage(0,0,fits.getWidth(), fits.getHeight());
        //BufferedImage image = UtilImageIO.loadImage(path);
        ImageUInt8 gray = ConvertBufferedImage.convertFromSingle(image, null, ImageUInt8.class);
        detect(gray);
        clustering();
        int histogram[] = new int[256];
        ImageStatistics.histogram(gray, histogram);
        havg=0; hmax=-1; hstd=0;
        for (int i=0; i<256; i++) {
            havg += histogram[i];
            if (hmax < histogram[i])
                hmax = histogram[i];
        }
        havg/=256;
        for (int i=0; i<256; i++) {
            hstd += (histogram[i]-havg)*(histogram[i]-havg);
        }
        hstd = Math.sqrt(hstd/255.0);
        //    System.out.print(histogram[i]+ " ");

        // test image
        /*
        double xscale=0.5, yscale=0.8;
        BufferedImage newImage = new BufferedImage(
                (int)(image.getWidth()*xscale), (int)(image.getHeight()*yscale), BufferedImage.TYPE_INT_RGB);
        JLabel lbl = new JLabel(new ImageIcon(newImage));
        add(lbl);
        Graphics2D g2d = newImage.createGraphics();
        g2d.drawImage(image, 0, 0, (int) (image.getWidth()*xscale), (int) (image.getHeight()*yscale), null);
        draw(g2d,xscale,yscale);
        g2d.dispose();
        */
    }

   /* public void generateFromStream(ByteArrayInputStream input){

    }*/
    
    public void generate(String path) throws FitsException, DataTypeNotSupportedException, NoImageDataFoundException, IOException {
        // generate features from the image file
        file = path;
        //File f=new File(path);
        //InputStream Stream = new FileInputStream(f);
        //FITSImage fits = new FITSImage(Stream);
        //Fits fit=new Fits(Stream);
        FITSImage fits = new FITSImage(path);
        BufferedImage image = fits.getSubimage(0,0,fits.getWidth(), fits.getHeight());
        //BufferedImage image = UtilImageIO.loadImage(path);
        ImageUInt8 gray = ConvertBufferedImage.convertFromSingle(image, null, ImageUInt8.class);
        detect(gray);
        clustering();
        int histogram[] = new int[256];
        ImageStatistics.histogram(gray, histogram);
        havg=0; hmax=-1; hstd=0;
        for (int i=0; i<256; i++) {
            havg += histogram[i];
            if (hmax < histogram[i])
                hmax = histogram[i];
        }
        havg/=256;
        for (int i=0; i<256; i++) {
            hstd += (histogram[i]-havg)*(histogram[i]-havg);
        }
        hstd = Math.sqrt(hstd/255.0);
        //    System.out.print(histogram[i]+ " ");

        // test image
        /*
        double xscale=0.5, yscale=0.8;
        BufferedImage newImage = new BufferedImage(
                (int)(image.getWidth()*xscale), (int)(image.getHeight()*yscale), BufferedImage.TYPE_INT_RGB);
        JLabel lbl = new JLabel(new ImageIcon(newImage));
        add(lbl);
        Graphics2D g2d = newImage.createGraphics();
        g2d.drawImage(image, 0, 0, (int) (image.getWidth()*xscale), (int) (image.getHeight()*yscale), null);
        draw(g2d,xscale,yscale);
        g2d.dispose();
        */
    }

    private <T extends ImageSingleBand>
    void detect( T input) {

        // Create a Fast Hessian detector from the SURF paper.
        // Other detectors can be used in this example too.
        InterestPointDetector<T> detector = FactoryInterestPoint.fastHessian(
                new ConfigFastHessian(10, 2, 100, 2, 9, 3, 4));

        // find interest points in the image
        detector.detect(input);
        for( int i = 0; i < detector.getNumberOfFeatures(); i++ ) {
            Point2D_F64 pt = detector.getLocation(i);
            points0.add(new Point(pt, detector.getScale(i)));
        }
    }


    private void clustering(){
        // use linkage to cluster points0 vector
        // generate "points" and "lines"
        Hashtable <Point, HashSet<Point>> t = new Hashtable<Point, HashSet<Point>>();
        for (Point p: points0){
            HashSet<Point> s = new HashSet<Point>();
            s.add(p);
            t.put(p, s);
            //System.out.println(s.size());
        }
        

        // parameters for clustering points to lines
        double th = 60; // y-distance between two points < th -> on the same line
        double mindx=3; // x-distance <mindx -> on the same line

        // merge points
        for (Point p: points0){
            HashSet<Point> s = t.get(p);

            for (Point p1: points0){
                if (p!=p1 &&( Math.abs(p.getX()-p1.getX())<mindx ||p.distance(p1)<th)){
                    HashSet<Point> s1 = t.get(p1);
                   // System.out.println(s1.size());
                    s.addAll(s1);
                    t.put(p1, s);
                }
            }
        }
        

        HashSet<HashSet<Point> > cs = new HashSet<HashSet<Point>>();

        // collecting clusters
        for (Point p: points0){
            cs.add(t.get(p));
        }

        // generating points and lines
        for (HashSet<Point> s: cs){
            if (s.size()==1){
                for (Point p: s)
                    points.add(p); // individual points
            }
            else{
                // lines are almost always vertical
                double maxy=0, miny=10000;
                Point start=null, end=null;
                for (Point p: s){
                    double y = p.getY();
                    if (maxy< y){
                        maxy = y;
                        end=p;
                    }
                    if (miny> y){
                        miny = y;
                        start=p;
                    }
                }
                if (maxy-miny<=mindx)
                    points.add(start);
                else
                    lines.add(new Line(start, end));
            }
        }
        /*
        for (Point p: points){
            System.out.println("point:"+p);
        }
        for (Line l: lines){
            System.out.println("line:"+l);
        }
        */
    }

    public int nLines(){
        return lines.size();
    };

    public int nPoints(){
        return points.size();
    };

    public JSONObject encode(){
        JSONObject o = new JSONObject();
        JSONArray jp = new JSONArray();
        JSONArray jl = new JSONArray();
        try {
            for (Point p : points)
                jp.put(p.encode());
            o.put("points", jp);
            for (Line l: lines)
                jl.put(l.encode());
            o.put("lines", jl);
            JSONObject h = new JSONObject();
            h.put("hmax", hmax);
            h.put("havg", havg);
            h.put("hstd", hstd);
            o.put("histogram", h);
        }catch(Exception e){
            e.printStackTrace();
        }

        return o;
    }

    public void decode(JSONObject o){
        lines = new Vector<Line>();
        points = new Vector<Point>();
        points0=null;
        try{
            JSONArray a = (JSONArray)o.get("lines");
            for (int i=0; i<a.length(); i++){
                Line l= new Line();
                lines.add(l );
                l.decode(a.getJSONObject(i));
            }
            a = (JSONArray)o.get("points");
            for (int i=0; i<a.length(); i++){
                Point p= new Point();
                points.add(p);
                p.decode(a.getJSONObject(i));
            }
            JSONObject h = (JSONObject) o.get("histogram");
            hmax = h.getDouble("hmax");
            havg= h.getDouble("havg");
            hstd = h.getDouble("hstd");
        }catch(Exception e){
            e.printStackTrace();
        }
        //System.out.println("points "+points);
        //System.out.println("lines" + lines);
    }

    public String getFeatures1(){
        // csv format
        double maxr=0, minr=10000;
        double maxthick =0, minthick=10000;
        double maxlen=0, minlen=10000;
        for (Point p:points) {
            double r = p.getR();
            if (maxr < r)
                maxr = r;
            if (minr > r)
                minr = r;
        }
        for (Line l: lines){
            double t = l.thickness();
            double d = l.length();
            if (maxthick < t ){
                maxthick = t;
            }
            if (minthick>t)
                minthick=t;
            if (maxlen<d)
                maxlen=d;
            if(minlen>d)
                minlen=d;
        }

        String s="";
        s+=havg+",";
        s+=hstd+",";
        s+=hmax+",";
        s+=maxr+",";
        s+=maxthick+",";
        s+=maxlen+",";
        s+=minr+",";
        s+=minthick+",";
        s+=minlen+",";
        s+=points.size()+",";
        s+=lines.size();

        return s;
    }
    public void draw(Graphics2D g2d, double xscale, double yscale){
        g2d.setColor(Color.RED);

        for (Point p: points){
            p.draw(g2d, xscale, yscale);
        }
        g2d.setColor(Color.BLUE);
        for (Line l:lines){
            l.draw(g2d, xscale, yscale);
        }
    }
    }

