(ns route-ccrs.schema.dates
  (:require [schema.core :as s]
            #? (:cljs [goog.date :as gd])))

(def DateInst
  "A date is any instance of the run time environment's date class or
  a Joda time class."
  (s/either
    s/Inst
    #?@(:clj [org.joda.time.DateTime
              org.joda.time.DateMidnight
              org.joda.time.LocalDate
              org.joda.time.LocalDateTime]
        :cljs [gd/Date
               gd/DateTime
               gd/UtcDateTime])))
