
mkdir %KXML2%\lib

cd %XMLPULL%\src\java\api

javac -g:none -O -d %XMLPULL%\classes org\xmlpull\v1\*.java

cd %KXML2%\src

javac -classpath %XMLPULL%\classes -g:none -O -d %KXML2%/classes org/kxml2/io/*.java

cd %KXML2%\classes

jar cfM %KXML2%\lib\kxml2-min.zip org/kxml2/io/KXmlParser.class
jar cfM %KXML2%\lib\kxml2.zip org/kxml2/io/*.class META-INF/services/org.xmlpull.v1.XmlPullParserFactory

cd %XMLPULL%\classes

jar ufM %KXML2%/lib/kxml2-min.zip org/xmlpull/v1/XmlPullParser.class org/xmlpull/v1/XmlPullParserException.class
jar ufM %KXML2%/lib/kxml2.zip org/xmlpull/v1/*.class


cd %KXML2%\build