
mkdir %KXML2%\lib
mkdir %KXML2%\html
mkdir %KXML2%\html\apidoc

cd %KXML2%\html\apidoc
javadoc  org.kxml2.io org.xmlpull.v1

cd %KXML2%\src

javac org/kxml2/io/KXmlParser.java

jar cfM %KXML2%\lib\kxml2-src.zip org/kxml2/io/*.java
jar cfM %KXML2%\lib\kxml2-min.zip org/kxml2/io/*.class
jar cfM %KXML2%\lib\kxml2.zip org/kxml2/io/*.class

cd %XMLPULL%\src\java\api

javac org/xmlpull/v1/*.java

jar ufM %KXML2%/lib/kxml2-src.zip org/xmlpull/v1/*.java
jar ufM %KXML2%/lib/kxml2.zip org/xmlpull/v1/*.class
jar ufM %KXML2%/lib/kxml2-min.zip org/xmlpull/v1/XmlPullParser.class org/xmlpull/v1/XmlPullParserException.class



cd %KXML2%\build