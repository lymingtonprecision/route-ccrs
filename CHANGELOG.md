# Change Log

## [Unreleased][unreleased]

### Changed

* Use the primary supplier’s manufacturing lead time for purchase parts,
  where available. Fallback to the inventory part purchase lead time
  when there is no primary supplier or the lead time has not been
  entered.

## [3.5.0] - 2016-12-01

### Changed

* Excluding MRP order code ‘B’ parts from structures. These are long
  lead time parts for which we maintain safety stocks/use KANBAN and so
  do not want to have them unduly impact the leadtimes of their parents.

## [3.4.0] - 2016-05-19

### Changed

* All route/operation details are now retrieved from the “Routed
  Operations” suite of PL/SQL packages that drive our DBR
  implementation. The biggest change this introduces is that the
  operation buffers are now pulled from the database rather than
  being calculated. It also ensures parity between the systems.

## [3.3.0] - 2015-05-18

### Added

* New implementation of `path-from-root-to-loc` that actually returns
  the _path_ rather than a sequence of record IDs. (Paths being
  something that contains `:structs`, `:routes`, and `:components`
  keys and can be given to the `*-in` `fn`s.)

### Changed

* Renamed all `path-from-...` `fn`s to `ids-from-...`. "Path" implied
  something that could be used with the core `*-in` `fn`s (`get-in`,
  `assoc-in`, etc.) which was not the case. The `fn`s return sequences
  of record `:id`s making `ids-from-...` more appropriate.

## [3.2.0] - 2015-05-05

### Changed

* `get-part` now returns a variant rather than just `nil` or the part
  record. The return value can be either `[:ok part]` or `[:error err]`
  this allows us to actually report validation errors caused by issues
  with the data (in the hope that the user might fix them.)
* Replaced dependency information with Clojars version badge in README.
* Moved Reader Conditional notice up to top of "Usage" section in
  README.

### Removed

* `active-parts` as it was a remnant of the batch processing days and
  served no real purpose in the context of general use of this library.
* `valid_product_structures` IAL as we want to return information
  about invalid structures.
* Conditionals around `Part` schema in `part-zipper`, they were there
  from before recursive schema were supported in ClojureScript.

## [3.1.0] - 2015-05-05

### Added

* Route calculation results now include a `:ccr-queue` field which
  contains the number of days the routing will queue at the CCR before
  being worked on.

* `part-zipper` now exposes aliases of all the common `clojure.zip`
  `fn`s: `left`, `right`, `up`, `down`, `leftmost`, `rightmost`, `next`,
  `prev`, `lefts`, `rights`, `children`, and `end?`. (This should do
  away with most instances of having to require both namespaces.)

* `path-from-loc-to-root` and `path-from-root-to-loc` in `part-zipper`
  which give the _full_ list of keys needed to navigate from/to an
  arbitrary location in a part structure (given a zipper pointing to
  that location.)

### Changed

* Mass migration of files to Reader Conditionals and `.cljc`. Everything
  except those `fn`s/namespaces related to database interaction are now
  available under both Clojure and ClojureScript.

* Bumped Clojure dependency to `1.7.0-beta2` and added ClojureScript
  `0.0-3211`.

* `Date` schema renamed to `DateInst` to avoid name conflicts in
  ClojureScript.

* `remove-best-end-dates` (and `remove-best-end-date`) has been moved to
  the `route-ccrs.best-end-dates` namespace (from
  `route-ccrs.best-end-dates.update`.) Partly to avoid converting the
  `update` namespace to `.cljc` and partly because it makes more sense
  for them to be there.

* `sourced?` is now in `route-ccrs.schema.parts` (again due to `.cljc`
  namespacing and because `utils` might be going away.)

* The `calculation` test now use the same `DummyResolver` as the other
  tests rather than using their own implementation of the calculator
  protocols.

* `best-end-date` and `start-date` are now implemented using `cond`
  rather than multi-methods as the later wasn't working in
  ClojureScript.

* All the test generators have been split out of the `*_test.clj` files
  into their own namespaces.

* Added tests to ensure top level parts have descriptions.

[unreleased]: https://github.com/lymingtonprecision/route-ccrs/compare/3.4.0...HEAD
[3.5.0]: https://github.com/lymingtonprecision/route-ccrs/compare/3.4.0...3.5.0
[3.4.0]: https://github.com/lymingtonprecision/route-ccrs/compare/3.3.0...3.4.0
[3.3.0]: https://github.com/lymingtonprecision/route-ccrs/compare/3.2.0...3.3.0
[3.2.0]: https://github.com/lymingtonprecision/route-ccrs/compare/3.1.0...3.2.0
[3.1.0]: https://github.com/lymingtonprecision/route-ccrs/compare/3.0.0...3.1.0
