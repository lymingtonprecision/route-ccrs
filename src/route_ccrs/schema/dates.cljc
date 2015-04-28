(ns route-ccrs.schema.dates
  (:require [schema.core :as s]
            #? (:cljs [goog.date :as gd])))

(def Date
  "A date is any instance of the run time environment's date class or
  a Joda time class.

  **Warning:** if you refer to this schema in ClojureScript _always_ do
  so as a namespace qualified var, _never_ `:refer` it into the current
  namespace. Failure to heed this warning will result in strange errors
  when the var gets replaced with a reference to some other `Date`
  object."
  (s/either
    s/Inst
    #?@(:clj [org.joda.time.DateTime
              org.joda.time.DateMidnight
              org.joda.time.LocalDate
              org.joda.time.LocalDateTime]
        :cljs [gd/Date
               gd/DateTime
               gd/UtcDateTime])))
