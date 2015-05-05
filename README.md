# route-ccrs

A library for calculating the currently achievable best end date and
constraining resource of parts, structures, and routings from IFS.

## Installation

Add the following dependency to your `project.clj` file:

[![Clojars Project](http://clojars.org/lymingtonprecision/route-ccrs/latest-version.svg)](http://clojars.org/lymingtonprecision/route-ccrs)

## Usage

**Note:** this library is dependant upon Clojure's [Reader
Conditionals](http://dev.clojure.org/display/design/Reader+Conditionals)
make sure you're using a version of Clojure(Script) that supports them.

### From Clojure

There are two provided
[components](https://github.com/stuartsierra/component):

* `route-ccrs.part-store/IFSPartStore` which creates `Part` records from
  the part, structure, and routing data within IFS. These part records
  form the basis of the inputs to all other `fn`s in the library.
* `route-ccrs.best-end-dates.calculator/IFSDateCalculator` calculates
  end dates based on the calendars and work center loads in IFS, given
  various parameters about the new load requirement. Unlikely to be
  used directly but required as a parameter to most of the `update-*`
  `fn`s.

Both components have a single dependency: a database connection,`:db`,
that can be used as the `db-spec` in JDBC calls.

This library makes no assumptions on how such a connection is
established or provided but it is suggested that you use [Hikari
CP][hikari-cp] (and it's [Clojure bindings][hikari-clj].) (Just be
mindful of the `:minimum-idle` and `:maximum-pool-size`
settings&mdash;we don't want to consume all the database processes.)

[hikari-cp]: https://github.com/brettwooldridge/HikariCP
[hikari-clj]: https://github.com/tomekw/hikari-cp

The vast majority of the `fn`s you will wish to use are under the
`route-ccrs.best-end-dates.*` namespaces. This covers updating and
extracting the end dates of part records, and their child records.

### From ClojureScript

Whilst ClojureScript clients can't expect to be able to directly use the
components that interact with the database they _do_ have access to the
schema and utility `fn`s that make working with the defined records a
little bit easier.

Currently the following namespaces are available in both runtimes:

* `route-ccrs.schema.*`
* `route-ccrs.best-end-dates`
* `route-ccrs.best-end-dates.maps`
* `route-ccrs.manufacturing-methods`
* `route-ccrs.part-zipper`
* `route-ccrs.start-dates`

(See: [ClojureScript Client Dev](#clojurescript-client-dev) below for
development details.)

### Example

```clojure
(ns route-ccrs.example
  (:require [clojure.pprint :refer [pprint]]
            [hikari-cp.core :as hk]
            [com.stuartsierra.component :as component]
            [route-ccrs.part-store :as ps]
            [route-ccrs.best-end-dates :refer [best-end-date]]
            [route-ccrs.best-end-dates.calculator :as dc]
            [route-ccrs.best-end-dates.update :refer :all]))

(defrecord IFS [host instance user password]
  component/Lifecycle
  (start [this]
    (let [o (merge hk/default-datasource-options
                   {:adapter "oracle"
                    :driver-type "thin"
                    :server-name host
                    :port-number 1521
                    :database-name instance
                    :username user
                    :password password
                    :minimum-idle 1
                    :maximum-pool-size 10})
          ds (hk/make-datasource o)]
      (assoc this :options o :datasource ds)))
  (stop [this]
    (if-let [ds (:datasource this)]
      (hk/close-datasource ds))
    (dissoc this :datasource)))

(defn system [host instance user password]
  (let [s (component/system-map
           :db (->IFS host instance user password)
           :part-store (ps/ifs-part-store)
           :date-calculator (dc/ifs-date-calculator))]
    (component/start s)))

(let [sys (system "database-server" "database-instance" "user" "password")
      pid {:id "100100012R03"}
      [r p] (ps/get-part (:part-store sys) pid)
      up (if (= :ok r)
           (update-all-best-end-dates-under-part p (:date-calculator sys)))
      _ (component/stop sys)]
  (if (= :ok r)
    (do
      (pprint (str (:id pid) "'s best end date is " (best-end-date up)))
      (pprint "The full structure is:")
      (pprint up))
    (do
      (pprint (str "Error processing part " (:id pid) ":"))
      (pprint p))))
```

## Pre-Requisites

The calculations performed by this library are dependant upon the
free work capacity periods generated by the `finite-capacity-load`
program.

Additionally, the library requires use of an IAL&mdash;the
definition of which is in the `resources/ials` folder:

* `active_structure_routings`
  Lists all currently active _and tentative_ product structure/routing
  combinations.

### Database permissions

The user account used to establish the database connection given to the
components in this library must have the following access rights:

    grant create session to routeccr;
    -- selects
    grant select on ifsapp.inventory_part to routeccr;
    grant select on ifsapp.inventory_part_planning to routeccr;
    grant select on ifsapp.inventory_part_status_par to routeccr;
    grant select on ifsinfo.inv_part_cust_part_no to routeccr;
    grant select on ifsapp.manuf_part_attribute to routeccr;
    grant select on ifsapp.prod_structure to routeccr;
    grant select on ifsapp.prod_structure_head to routeccr;
    grant select on ifsapp.prod_struct_alternate to routeccr;
    grant select on ifsapp.routing_head to routeccr;
    grant select on ifsapp.routing_alternate to routeccr;
    grant select on ifsapp.routing_operation to routeccr;
    grant select on ifsapp.work_center to routeccr;
    grant select on ifsapp.technical_object_reference to routeccr;
    grant select on ifsapp.technical_specification_both to routeccr;
    grant select on ifsapp.shop_ord to routeccr;
    grant select on ifsapp.purchase_order to routeccr;
    grant select on ifsapp.purchase_order_line to routeccr;
    -- IALs
    grant select on ifsinfo.active_structure_routings to routeccr;
    -- data from other programs
    grant select on finiteload.free_capacity to routeccr;
    -- apis
    grant execute on ifsapp.inventory_part_type_api to routeccr;
    grant execute on ifsapp.inventory_part_status_par_api to routeccr;
    grant execute on ifsapp.site_api to routeccr;
    grant execute on ifsapp.work_time_calendar_api to routeccr;

## Running Tests

The test suite is split into three pieces:

* The schema and basic record manipulation/interrogation functions.

      lein test route-ccrs.cljc-test

* Retrieving records from an IFS database.

      lein test :db

* Full end-to-end integration tests.

      lein test :integration

(The basic tests need to be run by specifying a wrapper namespace,
`route-ccrs.cljc-test`, because they're all now in `.cljc` files so
that we can run them under ClojureScript too, and `lein test` doesn't
currently load `.cljc` files directly.)

## <a name="clojurescript-client-dev">ClojureScript Client Dev</a>

Given that we're using reader conditionals, a beta version of Clojure,
and all the bleeding edge dependencies that this feature requires this
can be considered a "use at your own risk" branch.

ClojureScript testing is currently very basic. It requires
[PhantomJS](http://phantomjs.org/) to be available in the path and runs
a very basic test runner that just includes all the tests we've made
cross runtime (converted to `.cljc` files.)

The `cljsbuild` hooks are currently disabled because it slows things
down too much and there's no real need to have things compiled and
tested concurrently. As such, you need to run `lein cljsbuild` tasks
manually to check everything's still OK with the ClojureScript side of
things. `lein clean` will remove the compiled Javascript files as
they're output to the `target` directory.

## License

Copyright © 2014 Lymington Precision Engineers Co. Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
