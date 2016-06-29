(ns dbf.core
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.io BufferedInputStream FileInputStream
            OutputStreamWriter FileOutputStream PushbackReader])
  (:gen-class))

(def BUFFER-SIZE 8192)
#_(def db (BufferedInputStream.
           (FileInputStream.
            "D:/KOZLOV/alEX/temp/tmp/ogk02/M000103.DBF")
           BUFFER-SIZE))

(defn byte-to-int
  "Convert two or four bytes to integer value"
  ([b1 b2]
   (byte-to-int b1 b2 0 0))
  ([b1 b2 b3 b4]
   (bit-or b1
           (bit-shift-left b2 8)
           (bit-shift-left b3 16)
           (bit-shift-left b4 24))))

(defn bytes-to-str
  "Convert given bytes to string representation"
  [& bytes]
  (-> bytes
      byte-array
      (String. (java.nio.charset.Charset/forName "CP866"))
      str/trim))

(defn read-bytes!
  "Read specific number of bytes from file"
  [file n]
  (doall (for [_ (range n)]
           (.read ^BufferedInputStream file))))

(defn read-db-meta
  "Read dbf`s file meta.

  Take a file name string and returns a map"
  [file]
  (with-open [db (BufferedInputStream.
                  (FileInputStream. ^String file) BUFFER-SIZE)]
   {:db-version (.read ^BufferedInputStream db)
    :last-modified {:year (.read ^BufferedInputStream db)
                    :month (.read ^BufferedInputStream db)
                    :day (.read ^BufferedInputStream db)}
    :num-records (apply byte-to-int (read-bytes! db 4))
    :first-offset (apply byte-to-int (read-bytes! db 2))
    :record-length (apply byte-to-int (read-bytes! db 2))}))

(defn read-records-meta
  "Read records metadata

  Take file as string and offset of first dbf data record. Return
  sequence of records meta maps"
  ([file]
   (read-records-meta file (:first-offset (read-db-meta file))))
  ([file first-offset] 
   (with-open [db (BufferedInputStream.
                   (FileInputStream. ^String file) BUFFER-SIZE)]
     (.skip ^BufferedInputStream db 32) ;;skip main dbf meta - first 32b
     (doall
      (map #(dissoc % :dumb)
           (for [_ (range (/ (- first-offset 32 1) 32))]
             {:name 
              (apply bytes-to-str
                     (filter
                      (complement zero?)
                      (for [_ (range 11)]
                        (.read ^BufferedInputStream db))))
              :type (char (.read ^BufferedInputStream db))
              :offset (apply byte-to-int (read-bytes! db 4))
              :length (.read ^BufferedInputStream db)
              :fractional-length (.read ^BufferedInputStream db)
              :dumb (.skip ^BufferedInputStream db 14)}))))))

(defn read-dbf-meta
  "Read full meta map of dbf file"
  [file]
  (let [db-meta (read-db-meta file)]
    (assoc db-meta
           :fields (vec (read-records-meta
                         file
                         (:first-offset db-meta))))))

(def conv-functs)

(defn read-records!
  "Return a lazy sequence of record maps. Because of leziness the
  function must be used in a scope of opened dbf file. It also take
  dbf metadata and map of conversion function. A conv map contains
  keys as field names like {:feild-name conv-function, ...} and may by
  used in special cases, othewise - empty map."
  [dbf dbf-meta conv]
  (let [{:keys [first-offset
                fields 
                num-records]} dbf-meta
        _ (.skip ^BufferedInputStream dbf first-offset)]
    (for [_ (range num-records)]
      (let [tmp (assoc {} :deleted
                       (if (= 0x20 (.read ^BufferedInputStream dbf))
                         false true))]
        #_(println "[" x "]")
        (reduce
         (fn [m {:keys [name type length fractional-length]}]
           (let [kv (keyword (str/lower-case name))]
             (assoc m kv
                    (cond
                      (contains? conv kv)
                      (apply ((kv conv) conv-functs) (read-bytes! dbf length))
                      (= type \C) (apply bytes-to-str
                                         (read-bytes! dbf length))
                      (= type \N) (let [s (apply bytes-to-str
                                                 (read-bytes! dbf length))
                                        val (if (or (str/blank? s) (= s "."))
                                              0.0
                                              (java.math.BigDecimal.
                                               ^String s))]
                                    (if (= fractional-length 0)
                                      (int val)
                                      (double val)))
                      :default (read-bytes! dbf length)))))
         tmp fields)))))

(defn export-to-csv!
  "Write data from dbf in-file to csv out-file. The out-file will
  contain fields that included in fields vector. The keywords order
  in fields vector are also important. You can specify a :conv map
  that contain functions for some special conversion fields data. The
  function will by applyed on row byte data. Ather options
  are :encoding string and :delimiter string."
  [in-file out-file fields & {:keys [conv encoding delimiter]
      :or {conv {} encoding "UTF8" delimiter "|"}}]
  (let [dbf-meta (read-dbf-meta in-file)]
    (with-open [out-file (OutputStreamWriter.
                          (FileOutputStream. ^String out-file)
                          ^String encoding)
                dbf (BufferedInputStream. (FileInputStream. in-file)
                                          BUFFER-SIZE)]
      (binding [*out* out-file]
        (doseq [rec (read-records! dbf dbf-meta conv)]
          (when (not (:deleted rec))
            (println
             (apply str (interpose delimiter
                                   (for [field fields]
                                     (field rec)))))))))))

(defn chars-to-int
  "Example of function that convert three characters to integer
  value. (It was a case when developer use a three char as index in his
  tables may be for space saving.)"
  [c1 c2 c3]
  (byte-to-int (dec  c3) (dec c2) (dec c1) 0))

(def conv-functs {:chars-to-int chars-to-int
                  :bytes-to-str bytes-to-str})

(defn -main
  "Convert db files to csv"
  [& args]
  (let [import-config (with-open [reader (io/reader (first args))]
                        (edn/read (PushbackReader. reader)))]
    (doseq [conf import-config]
      (export-to-csv! (:in-file conf)
                      (:out-file conf)
                      (:fields conf)
                      :conv (or (:conv conf) {})
                      :encoding (or (:encoding conf) "UTF8")
                      :delimiter (or (:delimiter conf) "|")))))
