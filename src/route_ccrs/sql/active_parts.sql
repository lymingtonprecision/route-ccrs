with active_part_statuses as (
  select
    ps.part_status
  from ifsapp.inventory_part_status_par ps
  where ps.demand_flag_db = 'Y'
     or ps.onhand_flag_db = 'Y'
     or ps.supply_flag_db = 'Y'
)
-- purchased raw parts
select
  ip.part_no,
  mpa.low_level low_level_code
from ifsapp.inventory_part ip
join ifsapp.manuf_part_attribute mpa
  on ip.contract = mpa.contract
  and ip.part_no = mpa.part_no
where ip.type_code_db = '3'
  and ip.part_status in (select part_status from active_part_statuses)
--
union all
--
-- structured parts
select
  ip.part_no,
  mpa.low_level low_level_code
from ifsapp.inventory_part ip
join ifsapp.manuf_part_attribute mpa
  on ip.contract = mpa.contract
  and ip.part_no = mpa.part_no
where ip.type_code_db <> '3'
  and exists (
    select
      *
    from ifsinfo.valid_product_structures vps
    where ip.contract = vps.contract
      and ip.part_no = vps.part_no
  )
  and ip.part_status in (select part_status from active_part_statuses)
