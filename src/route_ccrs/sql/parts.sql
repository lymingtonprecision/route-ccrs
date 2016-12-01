select
  ip.part_no id,
  ipcp.cust_part_no customer_part,
  ipcp.issue issue,
  ipcp.description,
  decode(
    ip.type_code_db,
    3, 'raw',
    'structured'
  ) type,
  ip.purch_leadtime lead_time,
  decode(
    ip.type_code_db,
    3, ifsapp.work_time_calendar_api.get_end_date(
      ifsapp.site_api.get_manuf_calendar_id(ip.contract),
      trunc(sysdate),
      ip.purch_leadtime
    ),
    null
  ) best_end_date
from ifsapp.inventory_part ip
join ifsinfo.inv_part_cust_part_no ipcp
  on ip.part_no = ipcp.part_no
where ip.contract = 'LPE'
  and (
    (:part_no is not null and ip.part_no = :part_no) or
    (:part_no is null and ip.part_no in (
      select
        ps.component_part
      from ifsapp.prod_structure ps
      join ifsapp.inventory_part_planning ipp
        on ps.contract = ipp.contract
        and ps.component_part = ipp.part_no
      where ps.contract = 'LPE'
        and ps.part_no = :parent_part_no
        and ps.bom_type_db = decode(
          :bom_type,
          'manufactured', 'M',
          'repair', 'F',
          'purchased', 'P',
          null
        )
        and ps.eng_chg_level = :revision
        and ps.alternative_no = :alternative
        and ipp.planning_method <> 'B'
    ))
  )
