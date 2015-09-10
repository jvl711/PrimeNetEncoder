javac -d ./build/classes/ ./src/jvl/PrimeNetEncoder/*.java

cd build/classes
jar cfe ../../dist/PrimeNetEncoder.jar jvl.primenetencoder.PrimeNetEncoder jvl/primenetencoder*
cd ../..


#FOR SAGE LINUX TESTING#
cp ./dist/PrimeNetEncoder.jar /opt/sagetv/server/JARs/
