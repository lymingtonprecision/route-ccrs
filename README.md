# route-ccrs

Calculates the current Capacity Constrained Resources for active
manufacturing methods (combination of structure and routing) in IFS.

Performs two types of calculation:

### Routing only

> If we had all the material how soon could we produce _x_?

The results of this calculation are stored in two tables:

* `routing_ccr_hist` a history log of all calculated CCR values.
* `routing_ccr` a slightly de-normalized table giving the CCR as per the
  last calculation and the previous CCR if there has been a different
  CCR calculated for the routing in the past.

### Full Assembly

> Starting from nothing when could we have _x_?

Calculates, from the bottom up, all assembly lead times, determining
both what the CCR is and which component is most constrained (and
causing the longest delay before production can be started.)

Populates two tables:

* `assembly_ccr_hist` a history log all calculated values.
* `assembly_ccr`, as with `routing_ccr`, a slightly de-normalized
  table giving the results of the most recent calculation and
  those of the preceding calculation when different.

## Usage

First, set the following environment variables:

    DB_NAME=the name of the Oracle instance to connect to
    DB_SERVER=the name/address of the Oracle server
    DB_USER=the user to connect as
    DB_PASSWORD=the password to connect with

(Note: [environ](https://github.com/weavejester/environ) is being used
so these could be entered in `.lein-env` file.)

Then execute the `jar` file:

    java -jar <path\to\route-ccrs.jar>

Note that [Ragtime](https://github.com/weavejester/ragtime) doesn't
currently support loading migrations as resources from the JAR file. If
you want the migrations to be performed automatically when running from
a JAR you need to copy the `migrations` folder to the runtime directory.

### Pre-Requisites

`finite-capacity-load` generated free work center capacity periods.

`active_structure_routings` IAL created in `ifsinfo` schema (see
definition in `resources/ials`.)

### Requirements

In order to run successfully the user account used must have the
following access rights:

    grant create session to routeccr;
    grant create table to routeccr;
    alter user routeccr quota unlimited on users;
    -- selects
    grant select on ifsapp.inventory_part to routeccr;
    grant select on ifsapp.inventory_part_planning to routeccr;
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
    -- IALs
    grant select on ifsinfo.active_structure_routings to routeccr;
    -- data from other programs
    grant select on finiteload.free_capacity to routeccr;
    -- apis
    grant execute on ifsapp.inventory_part_type_api to routeccr;
    grant execute on ifsapp.inventory_part_status_par_api to routeccr;
    grant execute on ifsapp.site_api to routeccr;
    grant execute on ifsapp.work_time_calendar_api to routeccr;

As the user will be creating tables they will also need a suitable quota
on the tablespace (use `alter user <username> quota unlimited on
<tablespace>;` in a pinch.)

## License

Copyright © 2014 Lymington Precision Engineers Co. Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
