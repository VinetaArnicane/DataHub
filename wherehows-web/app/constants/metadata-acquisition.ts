import Ember from 'ember';
import { arrayMap } from 'wherehows-web/utils/array';
import { Classification, nonIdFieldLogicalTypes, NonIdLogicalType } from 'wherehows-web/constants/datasets/compliance';

/**
 * String indicating that the user affirms or ignored a field suggestion
 */
enum SuggestionIntent {
  accept = 'accept',
  ignore = 'ignore'
}

/**
 * Describes the index signature for the nonIdFieldLogicalTypes object
 */
interface INonIdLogicalTypes {
  [prop: string]: {
    classification: Classification;
    displayAs: string;
  };
}

/**
 * Describes the properties on a field identifier object for ui rendering
 */
interface IFieldIdProps {
  value: string;
  isId: boolean;
  displayAs: string;
}

/**
 * Describes the index signature for fieldIdentifierTypes
 */
interface IFieldIdTypes {
  [prop: string]: IFieldIdProps;
}

/**
 * Length of time between suggestion modification time and last modified time for the compliance policy
 * If a policy has been updated within the range of this window then it is considered as stale / or
 * has been seen previously
 * @type {number}
 */
const lastSeenSuggestionInterval: number = 7 * 24 * 60 * 60 * 1000;

/**
 * Percentage value for a compliance policy suggestion with a low confidence score
 * @type {number}
 */
const lowQualitySuggestionConfidenceThreshold = 0.5;

/**
 * A list of id logical types
 * @type {Array.<String>}
 */
const idLogicalTypes = ['ID', 'URN', 'REVERSED_URN', 'COMPOSITE_URN'];

/**
 * A list of custom logical types that may be treated ids but have a different behaviour from regular ids
 * @type {Array.<String>}
 */
const customIdLogicalTypes = ['CUSTOM_ID'];

/**
 * List of non Id field data type classifications
 * @type {Array}
 */
const genericLogicalTypes = Object.keys(nonIdFieldLogicalTypes).sort();

/**
 * A map of id logical types including custom ids to the default field classification for Ids
 * @type {Object}
 */
const idFieldDataTypeClassification: { [K: string]: Classification.LimitedDistribution } = [
  ...customIdLogicalTypes,
  ...idLogicalTypes
].reduce(
  (classification, idLogicalType) =>
    Object.assign(classification, { [idLogicalType]: Classification.LimitedDistribution }),
  {}
);

/**
 * Creates a mapping of nonIdFieldLogicalTypes to default classification for that field
 * @type {Object}
 */
const nonIdFieldDataTypeClassification: { [K: string]: Classification } = (<Array<NonIdLogicalType>>Object.keys(
  nonIdFieldLogicalTypes
)).reduce(
  (classification, logicalType) =>
    Object.assign(classification, {
      [logicalType]: nonIdFieldLogicalTypes[logicalType].classification
    }),
  {}
);
/**
 * A merge of id and non id field type classifications
 * @type {Object}
 */
const defaultFieldDataTypeClassification = Object.assign(
  {},
  idFieldDataTypeClassification,
  nonIdFieldDataTypeClassification
);

/**
 * Stores a unique list of classification values
 * @type {Array.<String>} the list of classification values
 */
const classifiers = Object.values(defaultFieldDataTypeClassification).filter(
  (classifier, index, iter) => iter.indexOf(classifier) === index
);
/**
 * A map of identifier types for fields on a dataset
 * @type {{none: {value: string, isId: boolean, displayAs: string}, member: {value: string, isId: boolean, displayAs: string}, subjectMember: {value: string, isId: boolean, displayAs: string}, group: {value: string, isId: boolean, displayAs: string}, organization: {value: string, isId: boolean, displayAs: string}, generic: {value: string, isId: boolean, displayAs: string}}}
 */
const fieldIdentifierTypes: IFieldIdTypes = {
  none: {
    value: 'NONE',
    isId: false,
    displayAs: 'Not an ID'
  },
  member: {
    value: 'MEMBER_ID',
    isId: true,
    displayAs: 'Member ID'
  },
  subjectMember: {
    value: 'SUBJECT_MEMBER_ID',
    isId: true,
    displayAs: 'Member ID (Subject Owner)'
  },
  group: {
    value: 'GROUP_ID',
    isId: true,
    displayAs: 'Group ID'
  },
  organization: {
    value: 'COMPANY_ID',
    isId: true,
    displayAs: 'Organization ID'
  },
  generic: {
    value: 'MIXED_ID',
    isId: false,
    displayAs: 'Mixed'
  },
  custom: {
    value: 'CUSTOM_ID',
    isId: false,
    // Although rendered as though an id, it's custom and from a UI perspective does not share a key similarity to other
    // ids, a logicalType / (field format) is not required to update this fields properties
    displayAs: 'Custom ID'
  },
  enterpriseProfile: {
    value: 'ENTERPRISE_PROFILE_ID',
    isId: true,
    displayAs: 'Enterprise Profile ID'
  },
  enterpriseAccount: {
    value: 'ENTERPRISE_ACCOUNT_ID',
    isId: true,
    displayAs: 'Enterprise Account ID'
  },
  contract: {
    value: 'CONTRACT_ID',
    isId: true,
    displayAs: 'Contract ID'
  },
  seat: {
    value: 'SEAT_ID',
    isId: true,
    displayAs: 'Seat ID'
  },
  advertiser: {
    value: 'ADVERTISER_ID',
    isId: true,
    displayAs: 'Advertiser ID'
  },
  slideshare: {
    value: 'SLIDESHARE_USER_ID',
    isId: true,
    displayAs: 'SlideShare User ID'
  }
};

/**
 * Checks if the identifierType is a mixed Id
 * @param {string} identifierType
 */
const isMixedId = (identifierType: string) => identifierType === fieldIdentifierTypes.generic.value;
/**
 * Checks if the identifierType is a custom Id
 * @param {string} identifierType
 */
const isCustomId = (identifierType: string) => identifierType === fieldIdentifierTypes.custom.value;

/**
 * Checks if an identifierType has a predefined/immutable value for the field format, i.e. should not be changed by
 * the end user
 * @param {string} identifierType the identifierType to check against
 * @return {boolean}
 */
const hasPredefinedFieldFormat = (identifierType: string) => {
  return isMixedId(identifierType) || isCustomId(identifierType);
};

/**
 * Gets the default logical type for an identifier type
 * @param {string} identifierType
 * @return {string | void}
 */
const getDefaultLogicalType = (identifierType: string): string | void => {
  if (isMixedId(identifierType)) {
    return 'URN';
  }
};

/**
 * Returns a list of logicalType mappings for displaying its value and a label by logicalType
 * @param {String} logicalType
 */
const logicalTypeValueLabel = (logicalType: string) =>
  (<{ [K: string]: Array<string> }>{
    id: idLogicalTypes,
    generic: genericLogicalTypes
  })[logicalType].map(value => ({
    value,
    label: nonIdFieldLogicalTypes[value]
      ? nonIdFieldLogicalTypes[value].displayAs
      : value.replace(/_/g, ' ').replace(/([A-Z]{3,})/g, value => Ember.String.capitalize(value.toLowerCase()))
  }));

// Map logicalTypes to options consumable by DOM
const logicalTypesForIds = logicalTypeValueLabel('id');

// Map generic logical type to options consumable in DOM
const logicalTypesForGeneric = logicalTypeValueLabel('generic');

/**
 * Caches a list of field identifiers
 * @type {Array<IFieldIdProps>}
 */
const fieldIdentifierTypesList: Array<IFieldIdProps> = arrayMap(
  (fieldIdentifierType: string) => fieldIdentifierTypes[fieldIdentifierType]
)(Object.keys(fieldIdentifierTypes));

/**
 * A list of field identifier types that are Ids i.e member ID, org ID, group ID
 * @type {Array<Pick<IFieldIdProps, 'value'>>}
 */
const fieldIdentifierTypeIds: Array<string> = fieldIdentifierTypesList
  .filter(({ isId }) => isId)
  .map(({ value }) => value);

/**
 * Caches a list of fieldIdentifierTypes values
 * @type {Array<Pick<IFieldIdProps, 'value'>>}
 */
const fieldIdentifierTypeValues: Array<string> = fieldIdentifierTypesList.map(({ value }) => value);

export {
  defaultFieldDataTypeClassification,
  classifiers,
  fieldIdentifierTypes,
  fieldIdentifierTypeIds,
  fieldIdentifierTypeValues,
  idLogicalTypes,
  customIdLogicalTypes,
  nonIdFieldLogicalTypes,
  isMixedId,
  isCustomId,
  hasPredefinedFieldFormat,
  logicalTypesForIds,
  logicalTypesForGeneric,
  getDefaultLogicalType,
  lastSeenSuggestionInterval,
  SuggestionIntent,
  lowQualitySuggestionConfidenceThreshold
};
