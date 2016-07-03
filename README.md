# cljdbf

cljdbf is used to read data from binary FoxBase .dbf files and export it
to csv format.

## Installation

Download and compile the
[source](http://github.com/alexander982/cljdbf).  You need
[leiningen](http://leiningen.org/) 2.0+ and Oracle or Open JDK 1.6+ to
compile sources.

## Compilation

In console type

    $ lein uberjar

It will crate a .jar file in the project target directory.

## Usage

Before using cljdbf you need to prepare export config. The config is a
clojure edn file. You can use a [sample config](sample_export_config.edn) as an
example.  After that type:

    $ java -jar dbf-0.1.1-standalone.jar export_config.edn

Where export_config.edn is a file that you had prepared.

## License

Copyright © 2016 Alexander Kozlov

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
