javac -cp ./dist/lib/Sage.jar -d ./build/classes/ ./src/jvl/PrimeNetEncoder/*.java



cd build/classes

jar cfe ../../dist/PrimeNetEncoder.jar jvl.primenetencoder.PrimeNetEncoder jvl/primenetencoder*

