select
  ip.contract,
  ip.part_no,
  mpa.low_level lowest_level,
  coalesce(
    dbr_batch.value_no,
    ipp.max_order_qty,
    ipp.std_order_size
  ) batch_size
from ifsapp.inventory_part ip
join ifsapp.inventory_part_planning ipp
  on ip.contract = ipp.contract
  and ip.part_no = ipp.part_no
join ifsapp.manuf_part_attribute mpa
  on ip.contract = mpa.contract
  and ip.part_no = mpa.part_no
--
left outer join ifsapp.technical_object_reference ipor
  on ipor.lu_name = 'InventoryPart'
  and ipor.key_value =
    ip.contract ||
    '^' || ip.part_no ||
    '^'
left outer join ifsapp.technical_specification_both dbr_batch
  on ipor.technical_spec_no = dbr_batch.technical_spec_no
  and dbr_batch.attribute = 'DBR_BATCH_SIZE'
--
where ip.type_code_db = ifsapp.inventory_part_type_api.encode('Manufactured')
  and ifsapp.inventory_part_status_par_api.get_supply_flag_db(ip.part_status) = 'Y'
order by
  mpa.low_level
