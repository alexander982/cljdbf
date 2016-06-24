(defproject dbf "0.1.1-SNAPSHOT"
  :description "Чтение(экспорт в CSV) DBF-файлов"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]]
  :main ^:skip-aot dbf.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
