# route-ccrs

Calculates the current Capacity Constrained Resources for active
routings in IFS.

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

### Requirements

In order to run successfully the user account used must have the
following access rights:

    grant create session to routeccr;
    grant create table to routeccr;
    alter user routeccr quota unlimited on users;
    -- selects
    grant select on ifsapp.inventory_part to routeccr;
    grant select on ifsapp.inventory_part_planning to routeccr;
    grant select on ifsapp.routing_head to routeccr;
    grant select on ifsapp.routing_alternate to routeccr;
    grant select on ifsapp.routing_operation to routeccr;
    grant select on ifsapp.work_center to routeccr;
    grant select on ifsapp.technical_object_reference to routeccr;
    grant select on ifsapp.technical_specification_both to routeccr;
    grant select on finiteload.free_capacity to routeccr;
    -- apis
    grant execute on ifsapp.inventory_part_status_par_api to routeccr;
    grant execute on ifsapp.work_time_calendar_api to routeccr;

As the user will be creating tables they will also need a suitable quota
on the tablespace (use `alter user <username> quota unlimited on
<tablespace>;` in a pinch.)

## License

Copyright © 2014 Lymington Precision Engineers Co. Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
