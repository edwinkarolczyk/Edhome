package com.edwinkarolczyk.edhome;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Reads text rows of a small XLSX without uploading a workbook or external OCR.
 * Intended for bank statements that Sheets opens as semicolon text in column A.
 * Legacy binary XLS, encrypted/complex workbooks and formula results are rejected.
 */
final class BankStatementWorkbook {
    private BankStatementWorkbook(){}

    static boolean isXlsx(byte[] content) {
        return content!=null&&content.length>=4
            &&content[0]=='P'&&content[1]=='K'
            &&content[2]==3&&content[3]==4;
    }

    static String textRows(byte[] content) {
        if(!isXlsx(content))throw new IllegalArgumentException(
            "To nie jest XLSX. Starszy XLS wymaga eksportu CSV.");
        byte[] sheet=null, shared=null;
        int files=0, total=0;
        try(ZipInputStream zip=new ZipInputStream(
                new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                if(++files>150)throw new IllegalArgumentException(
                    "Zbyt dużo elementów arkusza.");
                String name=entry.getName();
                if(!"xl/sharedStrings.xml".equals(name)
                        && !name.startsWith("xl/worksheets/sheet"))
                    continue;
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] buffer=new byte[4096];
                int n;
                while((n=zip.read(buffer))!=-1) {
                    total+=n;
                    if(total>4*1024*1024)
                        throw new IllegalArgumentException(
                            "Za duży arkusz po rozpakowaniu.");
                    out.write(buffer,0,n);
                }
                if("xl/sharedStrings.xml".equals(name))shared=out.toByteArray();
                if(name.matches("xl/worksheets/sheet[0-9]+[.]xml")
                        && sheet==null)sheet=out.toByteArray();
            }
        }catch(IllegalArgumentException invalid){throw invalid;}
        catch(Exception error){throw new IllegalArgumentException(
            "Nie można odczytać struktury XLSX.",error);}
        if(sheet==null)throw new IllegalArgumentException(
            "XLSX nie zawiera czytelnego arkusza.");
        List<String> strings=new ArrayList<>();
        if(shared!=null) {
            NodeList nodes=parse(shared).getElementsByTagName("si");
            if(nodes.getLength()>20000)throw new IllegalArgumentException(
                "Zbyt dużo tekstów XLSX.");
            for(int i=0;i<nodes.getLength();i++)
                strings.add(texts((Element)nodes.item(i),"t"));
        }
        NodeList rows=parse(sheet).getElementsByTagName("row");
        if(rows.getLength()>2000)
            throw new IllegalArgumentException("Za dużo wierszy XLSX.");
        StringBuilder result=new StringBuilder();
        for(int r=0;r<rows.getLength();r++) {
            Element row=(Element)rows.item(r);
            NodeList cells=row.getElementsByTagName("c");
            List<String> values=new ArrayList<>();
            boolean any=false;
            for(int c=0;c<cells.getLength();c++) {
                Element cell=(Element)cells.item(c);
                String type=cell.getAttribute("t");
                String value=texts(cell,"v");
                if("s".equals(type)) {
                    int index;
                    try{index=Integer.parseInt(value);}
                    catch(NumberFormatException error){
                        throw new IllegalArgumentException(
                            "Błędna referencja tekstu w XLSX.");}
                    if(index<0||index>=strings.size())
                        throw new IllegalArgumentException(
                            "Brak wartości tekstowej XLSX.");
                    value=strings.get(index);
                }else if("inlineStr".equals(type))value=texts(cell,"t");

                // Blank cells may be omitted from the XML. Preserve their
                // positions from the cell reference (for example G12), or a
                // valid bank row shifts left and is parsed under wrong headers.
                int column=columnIndex(cell.getAttribute("r"),c);
                while(values.size()<=column)values.add("");
                values.set(column,value);
                if(!value.isEmpty())any=true;
            }
            if(!any)continue;
            // Only trailing empties are disposable. Internal blanks are data.
            while(values.size()>1&&values.get(values.size()-1).isEmpty())
                values.remove(values.size()-1);
            if(values.size()==1)result.append(values.get(0));
            else for(int i=0;i<values.size();i++) {
                if(i>0)result.append(';');
                appendSemicolonField(result,values.get(i));
            }
            result.append('\n');
            if(result.length()>BankStatementCsv.MAX_BYTES)
                throw new IllegalArgumentException(
                    "Za dużo danych transakcyjnych w XLSX.");
        }
        return result.toString();
    }

    private static void appendSemicolonField(StringBuilder out,String value) {
        if(value.indexOf(';')<0&&value.indexOf('"')<0
                &&value.indexOf('\n')<0&&value.indexOf('\r')<0) {
            out.append(value);return;
        }
        out.append('"').append(value.replace("\"","\"\"")).append('"');
    }

    private static int columnIndex(String ref,int fallback) {
        if(ref==null||ref.isEmpty())return fallback;
        int value=0,letters=0;
        for(int i=0;i<ref.length();i++) {
            char ch=ref.charAt(i);
            if(ch>='A'&&ch<='Z') {
                value=value*26+(ch-'A'+1);letters++;
            } else if(ch>='a'&&ch<='z') {
                value=value*26+(ch-'a'+1);letters++;
            } else break;
        }
        return letters==0?fallback:value-1;
    }

    private static Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
            factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml));
        }catch(Exception error) {
            throw new IllegalArgumentException("Niebezpieczny lub błędny XML XLSX.",error);
        }
    }

    private static String texts(Element element,String tag) {
        NodeList nodes=element.getElementsByTagName(tag);
        StringBuilder value=new StringBuilder();
        for(int i=0;i<nodes.getLength();i++)
            value.append(nodes.item(i).getTextContent());
        return value.toString();
    }
}
