(ns route-ccrs.test-util
  (:require [user]))

(def test-system (atom nil))

(defn start-test-system []
  (if (nil? user/system)
    (user/init))
  (user/start)
  (reset! test-system user/system))
