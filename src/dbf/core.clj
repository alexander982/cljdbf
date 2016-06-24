(ns dbf.core
  (:require [clojure.string :as str])
  (:import [java.io BufferedInputStream FileInputStream
            OutputStreamWriter FileOutputStream])
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
              :rationale-length (.read ^BufferedInputStream db)
              :dumb (.skip ^BufferedInputStream db 14)}))))))

(defn read-dbf-meta
  "Read full meta map fo dbf file"
  [file]
  (let [db-meta (read-db-meta file)]
    (assoc db-meta
           :fields (vec (read-records-meta
                         file
                         (:first-offset db-meta))))))

(defn read-records!
  [dbf conv]
  (let [dbf-meta (read-record-meta! dbf (read-db-meta! dbf))
        {:keys [first-offset
                fields
                record-length
                num-records]} dbf-meta
        _ (.read ^BufferedInputStream dbf)
        ;; skip 1 byte = 0x0D - end of fields data block
        ]
    (for [x (range num-records)]
      (let [tmp (assoc {} :deleted
                       (if (= 0x20 (.read ^BufferedInputStream dbf))
                         false true))]
        #_(println "[" x "]")
        (reduce
         (fn [m {:keys [name type length]}]
           (let [kv (keyword (str/lower-case name))]
             (assoc m kv
                    (cond
                      (contains? conv kv)
                      (apply (kv conv) (read-bytes! dbf length))
                      (= type \C) (apply bytes-to-str
                                         (read-bytes! dbf length))
                      (= type \N) (let [s (apply bytes-to-str
                                                 (read-bytes! dbf length))]
                                    #_(println s)
                                    (if (or (str/blank? s) (= s "."))
                                      0.0
                                      (double
                                       (java.math.BigDecimal. ^String s))))
                      :default (read-bytes! dbf length)))))
         tmp fields)))))

(defn export-to-csv
  [fp recs fields conv encoding]
  (with-open [file (OutputStreamWriter. (FileOutputStream. ^String fp)
                                       ^String encoding)]
    (binding [*out* file]
      (doseq [rec recs]
        (when (not (:deleted rec))
          (println
           (apply str (interpose "|"
                                 (for [field fields]
                                   (if (contains? conv field)
                                     ((field conv) (field rec))
                                     (field rec)))))))))))

(defn chars-to-int
  [c1 c2 c3]
  (byte-to-int (dec  c3) (dec c2) (dec c1) 0))

(defn -main
  "Convert db files to csv"
  [& args]
  (with-open [db1 (BufferedInputStream.
                   (FileInputStream.
                    "M000101.DBF")
                   BUFFER-SIZE)
              db2 (BufferedInputStream.
                   (FileInputStream.
                    "M000102.DBF")
                   BUFFER-SIZE)
              db3 (BufferedInputStream.
                   (FileInputStream.
                    "M000103.DBF")
                   BUFFER-SIZE)]
    (export-to-csv "m000101_conv.csv"
                   (read-records! db1 {:kdse chars-to-int
                                       :kse chars-to-int})
                   [:kse :kdse :zona :poz :kol :kpk :datk]
                   {:kol int :kpk int}
                   "UTF8")
    (export-to-csv "m000102_conv.csv"
                   (read-records! db2 {:kdse chars-to-int})
                   [:kdse :obizi :nomdet :ndse :zol :ser :pl :pal]
                   {}
                   "UTF8")
    (export-to-csv "m000103_conv.csv"
                   (read-records! db3 {:ki chars-to-int
                                       :dat_vvod bytes-to-str})
                   [:ki :obiz :naim :kpprod :kol_zol :kol_ser :kol_plat
                    :kol_pal :ves_izd :ves_net :kod :dat_vvod]
                   {}
                   "UTF8")))
