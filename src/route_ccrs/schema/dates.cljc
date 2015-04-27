(ns route-ccrs.schema.dates
  (:require [schema.core :as s]))

(def Date
  "A date is any instance of the run time environment's date class or
  a Joda time class."
  (s/either
    s/Inst
    org.joda.time.DateTime
    org.joda.time.DateMidnight
    org.joda.time.LocalDate
    org.joda.time.LocalDateTime))
