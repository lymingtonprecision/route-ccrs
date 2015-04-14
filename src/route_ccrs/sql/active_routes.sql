select
  decode(
    ro.bom_type_db,
    'M', 'manufactured',
    'F', 'repair',
    'P', 'purchased',
    null
  ) route__type,
  ro.routing_revision route__revision,
  ro.alternative_no route__alternative,
  ra.alternative_description route__description,
  ro.operation_no id,
  ro.operation_description description,
  round(
    -- calculate per unit run time
    case ro.run_time_code_db
      when '1' then -- hours/unit
        greatest(
          ro.mach_run_factor,
          ro.labor_run_factor
        ) *
        coalesce(
          dbr_batch.value_no,
          ipp.max_order_qty,
          ipp.std_order_size
        )
      when '2' then -- units/hour
        case
          when greatest(ro.mach_run_factor, ro.labor_run_factor) = 0 then
            0
          else
            coalesce(
              dbr_batch.value_no,
              ipp.max_order_qty,
              ipp.std_order_size
            ) /
            greatest(
              ro.mach_run_factor,
              ro.labor_run_factor
            )
        end
      when '3' then -- hours
        case
          when ro.labor_run_factor > 0 then
            least(ro.mach_run_factor, ro.labor_run_factor)
          else
            ro.mach_run_factor
        end
      else
        0
    end
    -- factor for efficiency
    * (
      100 / decode(
        nvl(ro.efficiency_factor, 0),
        0, 100,
        ro.efficiency_factor
      )
    )
    -- convert to minutes
    * 60,
    2
  ) touch_time,
  --
  ro.work_center_no work_center,
  wc.description work_center_description,
  wc.average_capacity hours_per_day,
  decode(
    wc.work_center_code_db,
    'I', 'internal',
    'O', 'external',
    wc.work_center_code_db
  ) type,
  wc_ccr.value_text potential_ccr
from ifsapp.routing_operation ro
join ifsapp.routing_alternate ra
  on ro.contract = ra.contract
  and ro.part_no = ra.part_no
  and ro.bom_type_db = ra.bom_type_db
  and ro.routing_revision = ra.routing_revision
  and ro.alternative_no = ra.alternative_no
--
join ifsapp.inventory_part_planning ipp
  on ro.contract = ipp.contract
  and ro.part_no = ipp.part_no
left outer join ifsapp.technical_object_reference ipor
  on ipor.lu_name = 'InventoryPart'
  and ipor.key_value =
    ro.contract ||
    '^' || ro.part_no ||
    '^'
left outer join ifsapp.technical_specification_both dbr_batch
  on ipor.technical_spec_no = dbr_batch.technical_spec_no
  and dbr_batch.attribute = 'DBR_BATCH_SIZE'
--
join ifsapp.work_center wc
  on ro.contract = wc.contract
  and ro.work_center_no = wc.work_center_no
--
left outer join ifsapp.technical_object_reference wcor
  on wcor.lu_name = 'WorkCenter'
  and wcor.key_value =
    ro.contract ||
    '^' || ro.work_center_no ||
    '^'
left outer join ifsapp.technical_specification_both wc_ccr
  on wcor.technical_spec_no = wc_ccr.technical_spec_no
  and wc_ccr.attribute = 'CCR'
--
where (
    ro.contract,
    ro.part_no,
    ro.bom_type_db,
    ro.routing_revision,
    ro.alternative_no
  ) in (
    select distinct
      asr.contract,
      asr.part_no,
      asr.bom_type_db,
      asr.routing_revision,
      asr.routing_alternative
    from ifsinfo.active_structure_routings asr
    where asr.contract = 'LPE'
      and asr.part_no = :part_no
  )
--
order by
  ro.bom_type_db,
  ro.routing_revision,
  ro.alternative_no,
  ro.operation_no
