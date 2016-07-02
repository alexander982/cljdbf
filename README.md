# cljdbf

cljdbf is used to read data from binary FoxBase .dbf files and export it
to csv format.

## Installation

Download source from http://github.com/alexander982/FIXME. You also
need leiningen 2.0+ and Oracle or Open JDK 1.6+ to compile it.

## Compilation

In console type

    $ lein uberjar

It will crate a .jar file in target directory.

## Usage

Before using cljdbf you need to prepare export config. The config is a
clojure edn file. You can use a sample_export_config.edn as an
example.  After that type:

    $ java -jar dbf-0.1.0-standalone.jar export_config.edn

## License

Copyright © 2016 Alexander Kozlov

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
