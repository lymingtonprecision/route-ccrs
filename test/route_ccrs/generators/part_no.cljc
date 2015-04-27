(ns route-ccrs.generators.part-no
  (:require #?(:clj  [clojure.test.check.generators :as gen]
               :cljs [cljs.test.check.generators :as gen])
            #?(:clj  [com.gfredericks.test.chuck.generators :as gen'])))

(def gen-part-no
  #?(:clj (gen'/string-from-regex #"1001\d{5}R\d{2,}")
     :cljs (gen/elements ["100100001R01"
                          "100111111R11"
                          "100121354R878"
                          "100186106R687"
                          "100100897R09"
                          "100199999R9999999999"])))

(def gen-invalid-part-no
  (gen/one-of
    [#?(:clj (gen'/string-from-regex #"100([02-9]\d{5}|\d{6,})R(|.+)")
        :cljs (gen/elements ["100012346R01"
                             "100200876R01"
                             "100100186R"
                             "100840676RAB"
                             "1001123456R01"]))
     gen/string
     gen/simple-type]))
