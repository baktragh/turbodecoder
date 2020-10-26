#/bin/sh
#TURBO DECODER - DISTRIBUTION SCRIPT 1.0
#To be executed with MSYS2

#Basic configuration
VER_COMP=1.0.0
SRC_DIR=$1
DEST_BIN=$1/releases/turbodecoder-${VER_COMP}-bin
DEST_PARENT=$1/releases

#Create binary distribution
#############################

#Remove any previous outputs 
rm -rf ${DEST_BIN}
mkdir -p ${DEST_BIN}
mkdir ${DEST_BIN}/doc
mkdir ${DEST_BIN}/dist
rm -f ${DEST_PARENT}/turbodecoder-${VER_COMP}-bin.tar
rm -f ${DEST_PARENT}/turbodecoder-${VER_COMP}-bin.tar.bz2

#Copy basic files
cp ${SRC_DIR}/dist/turbodecoder.jar ${DEST_BIN}/dist
cp ${SRC_DIR}/turbodecoder.exe ${DEST_BIN}

#Copy documentation
cp ${SRC_DIR}/doc/turbodecoder.pdf ${DEST_BIN}/doc

#Create archive
OLDDIR=`pwd`
cd ${DEST_PARENT}
tar --exclude=".*" -cvf turbodecoder-${VER_COMP}-bin.tar turbodecoder-${VER_COMP}-bin
bzip2 turbodecoder-${VER_COMP}-bin.tar  
cd ${OLDDIR}