import { Button } from 'antd';
import styled from 'styled-components';

export const BaseButton = styled(Button)`
    &&& {
        display: flex;
        align-items: center;
        justify-content: center;
        border: none;
        box-shadow: none;
        border-radius: 50%;
    }
`;

export const RotatingButton = styled(BaseButton)<{ deg: number }>`
    transform: rotate(${(props) => props.deg}deg);
    transition: transform 250ms;
`;

export const BodyGridExpander = styled.div<{ isOpen: boolean }>`
    display: grid;
    grid-template-rows: ${(props) => (props.isOpen ? '1fr' : '0fr')};
    transition: grid-template-rows 250ms;
    overflow: hidden;
`;

export const BodyContainer = styled.div`
    min-height: 0;
`;
