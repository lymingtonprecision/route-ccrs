# Change Log

## [Unreleased][unreleased]

### Changed

* Replaced dependency information with Clojars version badge in README.
* Moved Reader Conditional notice up to top of "Usage" section in
  README.

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

[unreleased]: https://github.com/lymingtonprecision/route-ccrs/compare/3.1.0...HEAD
[3.1.0]: https://github.com/lymingtonprecision/route-ccrs/compare/3.0.0...3.1.0