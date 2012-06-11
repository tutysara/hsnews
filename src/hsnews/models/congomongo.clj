(ns
    ^{:author "Andrew Boekhoff",
      :doc "Various wrappers and utilities for the mongodb-java-driver"}
  hsnews.models.congomongo
  (:require [clojure.string])
  (:use
   [somnium.congomongo :exclude [with-ref-fetching]]
   [clojure.walk :only (postwalk)]
   [somnium.congomongo.config :only [*mongo-config*]]
   [somnium.congomongo.coerce :only [coerce coerce-fields coerce-index-fields]])
  (:import  [com.mongodb Mongo MongoOptions DB DBCollection DBObject DBRef ServerAddress WriteConcern Bytes]
            [com.mongodb.gridfs GridFS]
            [com.mongodb.util JSON]
            [org.bson.types ObjectId]))

;; Redefined with-ref-fetching to coerce db-ref and keep post-walking over the results
(defn with-ref-fetching
  "Returns a decorated fetcher fn which eagerly loads db-refs."
  [fetcher]
  (fn walker [& args]
    (let [as (or (second (drop-while (partial not= :as) args))
                 :clojure)
          coerce-db-ref (fn [v]
                          (coerce (.fetch ^DBRef v) [:mongo as]))]
      (postwalk (fn pwalk [x]
                  (if (db-ref? x)
                    (postwalk pwalk (coerce-db-ref x))
                    x))
                (apply fetcher args)))))
