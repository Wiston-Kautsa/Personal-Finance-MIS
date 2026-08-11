# PFMIS Asset Recognition

Budgets and completed projects must not automatically become assets.

## Recognition Flow

Asset recognition should follow this flow:

`budget -> approved expenditure -> transaction or project activity -> acquisition classification -> expense, consumable, inventory, or capital asset`

Only qualifying transaction lines or project activities may create an asset.

## Acquisition Classifications

Supported classifications:

- `CAPITAL_ASSET`
- `INVENTORY`
- `CONSUMABLE`
- `SERVICE`
- `OPERATING_EXPENSE`
- `PREPAYMENT`

## Required Asset Evidence

Before a capital asset is created, the application should require:

- qualifying source transaction or project activity
- acquisition classification
- ownership or control
- actual acquisition cost
- acquisition date
- date available for use
- expected useful life
- asset category
- capitalisation threshold result
- quantity
- unit cost
- location
- custodian or responsible person
- supporting reference
- approval status where applicable

## Duplicate Prevention

The application must prevent creating more than one capital asset from the same transaction line unless the transaction explicitly contains separate recognized asset lines.

## Asset Lifecycle

The target lifecycle includes:

- acquisition
- straight-line depreciation
- transfer
- maintenance
- impairment
- disposal
- write-off
- asset history
- asset reports

## Current Limitations

- The complete recognition workflow is not yet implemented.
- Legacy automatic registration paths must be audited and restricted before production use.
