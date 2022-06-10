import React from 'react';
import { GetDatasetQuery } from '../../../../../../graphql/dataset.generated';
import { CheckOwnership } from '../../../../dataset/whoAmI';
import { useBaseEntity } from '../../../EntityContext';
import { EditPropertiesTableEditable } from './EditPropertiesTableEditable';

export const EditPropertiesTab = () => {
    const dataset = useBaseEntity<GetDatasetQuery>();
    if (CheckOwnership(dataset)) {
        return (
            <>
                <EditPropertiesTableEditable />
            </>
        );
    }
    return (
        <>
            <span>You need to be a dataowner of this dataset to make edits</span>
        </>
    );
};
