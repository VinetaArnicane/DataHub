import { Button, Input, Modal } from 'antd';
import React, { useState } from 'react';

type Props = {
    onCloseModal: () => void;
    onOkOverride?: (result: string) => void;
    title?: string;
    defaultValue?: string;
};

export const EditTextModal = ({ defaultValue, onCloseModal, onOkOverride, title }: Props) => {
    const [stagedValue, setStagedValue] = useState(defaultValue || '');
    return (
        <Modal
            title={title}
            visible
            onCancel={onCloseModal}
            keyboard
            footer={
                <>
                    <Button onClick={onCloseModal} type="text">
                        Cancel
                    </Button>
                    <Button
                        id="addOwnerButton"
                        disabled={stagedValue.length === 0}
                        onClick={() => onOkOverride?.(stagedValue)}
                    >
                        Done
                    </Button>
                </>
            }
        >
            <Input onChange={(e) => setStagedValue(e.target.value)} value={stagedValue} />
        </Modal>
    );
};
