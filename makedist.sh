#/bin/sh
#TURGEN SYSTEM - DISTRIBUTION SCRIPT 1.6
#To be executed with MSYS2

#Basic configuration
VER_COMP=8.8.0
SRC_DIR=$1
DEST_BIN=/c/utils/releases/turgen_system-${VER_COMP}-bin
DEST_PARENT=/c/utils/releases
IS_COMPILER="/c/Program Files (x86)/Inno Setup 5/ISCC.exe"

#Create binary distribution
#############################

#Remove any previous outputs 
rm -rf ${DEST_BIN}
mkdir ${DEST_BIN}
mkdir ${DEST_BIN}/doc
rm ${DEST_PARENT}/turgen_system-${VER_COMP}-bin.tar
rm ${DEST_PARENT}/turgen_system-${VER_COMP}-bin.tar.bz2

#Copy basic files
cp ${SRC_DIR}/dist/turgen.jar ${DEST_BIN}
cp ${SRC_DIR}/ts.ico ${DEST_BIN}
cp ${SRC_DIR}/turgen.exe ${DEST_BIN}

#Copy documentation
cp ${SRC_DIR}/doc/src/turgen_system_doc.pdf ${DEST_BIN}/doc
cp ${SRC_DIR}/doc/COPYING ${DEST_BIN}/doc
cp ${SRC_DIR}/doc/CHANGES ${DEST_BIN}/doc

#Create archive
OLDDIR=`pwd`
cd ${DEST_PARENT}
tar --exclude=".*" -cvf turgen_system-${VER_COMP}-bin.tar turgen_system-${VER_COMP}-bin
bzip2 ${DEST_PARENT}/turgen_system-${VER_COMP}-bin.tar  
cd ${OLDDIR}

#Run Inno Setup
"${IS_COMPILER}" ts.iss